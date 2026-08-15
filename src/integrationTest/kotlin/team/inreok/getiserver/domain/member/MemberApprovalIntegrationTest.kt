package team.inreok.getiserver.domain.member

import com.redis.testcontainers.RedisContainer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import team.inreok.getiserver.domain.member.dto.ApprovalAction
import team.inreok.getiserver.domain.member.dto.MemberApprovalRequest
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.exception.MemberNotPendingException
import team.inreok.getiserver.domain.member.query.OAuthMemberPort
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.member.repository.MemberRoleRepository
import team.inreok.getiserver.domain.member.service.MemberApprovalService
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 교직원 가입 승인·거절의 동시성과 Token 연계를 실제 PostgreSQL로 검증한다(Issue #99 §6/§7/§9).
 * Service Test(Mock Repository)는 DB Row Lock 자체를 재현할 수 없어 이 Test에서만 확인된다.
 *
 * `MemberApprovalServiceImpl.process()`가 `MemberRepository.findByIdForUpdate`로 회원 Row에
 * `PESSIMISTIC_WRITE` Lock을 건 뒤 상태를 확인·전이하므로, 같은 PENDING 회원에 대한 동시 승인/거절은
 * 실제 DB 차원에서 순차적으로 처리되어 하나만 성공한다.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "app.jwt.secret=member-approval-integration-test-only-jwt-secret-value",
        "app.jwt.access-token-expiration-seconds=1800",
        "app.jwt.refresh-token-expiration-seconds=1209600",
        // application.yaml이 Classpath에서 우선하는데 app.file.storage는 Profile별 파일에만 있어
        // (Secret과 환경별 값) 여기서 채운다. 이 Test는 실제 Storage에 접속하지 않고 Bean 생성만 필요하다.
        "app.file.storage.bucket=geti-integration-test",
        "app.file.storage.region=us-east-1",
        "app.file.storage.access-key=integration-test-only-access-key",
        "app.file.storage.secret-key=integration-test-only-secret-key",
    ],
)
class MemberApprovalIntegrationTest {
    @Autowired
    private lateinit var memberApprovalService: MemberApprovalService

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var memberRoleRepository: MemberRoleRepository

    @Autowired
    private lateinit var oauthMemberPort: OAuthMemberPort

    @Test
    fun `같은 PENDING 교직원을 동시에 승인·거절하면 하나만 성공하고 상태와 Role이 모순되지 않는다`() {
        val memberId = createPendingGoogleTeacher("concurrent-approve-reject")

        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val results = ConcurrentLinkedQueue<Result<MemberStatus>>()
        val requests =
            listOf(
                MemberApprovalRequest(action = ApprovalAction.APPROVE),
                MemberApprovalRequest(action = ApprovalAction.REJECT, reason = "동시성 테스트 거절 사유"),
            )

        requests.forEach { request ->
            executor.submit {
                ready.countDown()
                start.await()
                results.add(runCatching { memberApprovalService.process(memberId, request).status })
            }
        }
        ready.await()
        start.countDown()
        executor.shutdown()
        check(executor.awaitTermination(30, TimeUnit.SECONDS)) { "동시 처리 Thread가 제한 시간 안에 끝나지 않았습니다." }

        val outcomes = results.toList()
        val successes = outcomes.filter { it.isSuccess }
        val failures = outcomes.mapNotNull { it.exceptionOrNull() }
        // 하나만 성공하고, 나머지는 이미 PENDING이 아니게 되어 409(MEMBER_NOT_PENDING)로 거부된다.
        assertThat(successes).hasSize(1)
        assertThat(failures).hasSize(1)
        assertThat(failures.single()).isInstanceOf(MemberNotPendingException::class.java)

        // DB 최종 상태와 Role이 성공한 처리와 일치해야 한다(부분 성공/모순 없음).
        val finalStatus = requireNotNull(memberRepository.findById(memberId).orElse(null)).status
        val hasTeacherRole = memberRoleRepository.findAllByIdMemberId(memberId).any { it.id.role == RoleType.TEACHER }
        assertThat(finalStatus).isEqualTo(successes.single().getOrThrow())
        when (finalStatus) {
            MemberStatus.ACTIVE -> assertThat(hasTeacherRole).isTrue()
            MemberStatus.REJECTED -> assertThat(hasTeacherRole).isFalse()
            else -> error("승인/거절 이후 상태는 ACTIVE 또는 REJECTED여야 합니다. (status=$finalStatus)")
        }
    }

    @Test
    fun `승인 후 최신 Role 조회에 TEACHER가 반영되어 Refresh 시 Token에 담긴다`() {
        val memberId = createPendingGoogleTeacher("approve-role-linkage")

        // 승인 전에는 Role이 없다(PENDING 교직원은 승인 우회 방지를 위해 Role 미부여).
        assertThat(oauthMemberPort.getRoles(memberId)).isEmpty()

        memberApprovalService.process(memberId, MemberApprovalRequest(action = ApprovalAction.APPROVE))

        // TokenService가 Refresh 시 사용하는 최신 Role 조회 경로(OAuthMemberPort.getRoles)에 TEACHER가
        // 반영되므로, 승인 이후 Refresh하면 Access Token의 roles에 TEACHER가 담긴다.
        assertThat(oauthMemberPort.getRoles(memberId)).containsExactly(RoleType.TEACHER.name)
    }

    private fun createPendingGoogleTeacher(subject: String): Long {
        val member =
            memberRepository.saveAndFlush(
                Member(
                    oauthProvider = OAuthProvider.GOOGLE,
                    oauthSubject = subject,
                    email = "$subject@example.com",
                    status = MemberStatus.PENDING,
                ),
            )
        return requireNotNull(member.id)
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18.4-alpine"))

        @Container
        @ServiceConnection
        @JvmStatic
        val redis = RedisContainer(DockerImageName.parse("redis:8.8.1-alpine"))
    }
}
