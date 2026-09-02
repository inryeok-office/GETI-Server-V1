package team.inreok.getiserver.domain.portfolio

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
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.MemberRole
import team.inreok.getiserver.domain.member.entity.MemberRoleId
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.member.repository.MemberRoleRepository
import team.inreok.getiserver.domain.portfolio.dto.PortfolioRequestCreateRequest
import team.inreok.getiserver.domain.portfolio.dto.PortfolioRequestStatusUpdateRequest
import team.inreok.getiserver.domain.portfolio.dto.PortfolioSubmissionUpsertRequest
import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioRequestStatus
import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioSubmissionStatus
import team.inreok.getiserver.domain.portfolio.repository.PortfolioSubmissionRepository
import team.inreok.getiserver.domain.portfolio.service.PortfolioRequestService
import team.inreok.getiserver.domain.portfolio.service.PortfolioSubmissionService
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 학생 제출(Upsert)의 동시성을 실제 PostgreSQL Pessimistic Lock으로 검증한다(Issue #204 리뷰 반영).
 * Service Test(Mock Repository)는 DB Row Lock을 재현할 수 없어 이 Test에서만 확인된다.
 *
 * `PortfolioSubmissionServiceImpl.submit()`이 `findByIdAndDeletedAtIsNullForUpdate`로 요청 Row에
 * `PESSIMISTIC_WRITE` Lock을 건 뒤 제출물을 Upsert하므로, 같은 학생의 동시 최초 제출이 순차화되어
 * 두 번째 요청이 첫 번째가 만든 Row를 보고 정상 Update를 탄다 -- UNIQUE 제약 위반(500) 없이 둘 다
 * 성공하고 제출물은 한 건만 남는다(Program 신청 동시성과 같은 관례).
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "app.jwt.secret=portfolio-concurrency-integration-test-only-jwt-secret-value",
        "app.jwt.access-token-expiration-seconds=1800",
        "app.jwt.refresh-token-expiration-seconds=1209600",
        // app.file.storage는 Profile 파일에만 있어(Secret·환경별 값) 여기서 채운다. 이 Test는 파일을
        // 첨부하지 않아 실제 Storage에 접속하지 않고 Bean 생성만 필요하다.
        "app.file.storage.bucket=geti-integration-test",
        "app.file.storage.region=us-east-1",
        "app.file.storage.access-key=integration-test-only-access-key",
        "app.file.storage.secret-key=integration-test-only-secret-key",
    ],
)
class PortfolioSubmissionConcurrencyIntegrationTest {
    @Autowired
    private lateinit var portfolioRequestService: PortfolioRequestService

    @Autowired
    private lateinit var portfolioSubmissionService: PortfolioSubmissionService

    @Autowired
    private lateinit var portfolioSubmissionRepository: PortfolioSubmissionRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var memberRoleRepository: MemberRoleRepository

    @Test
    fun `같은 학생이 동시에 두 번 제출해도 제출물은 하나만 생기고 둘 다 성공한다`() {
        val studentId = createActiveStudent("concurrency-submit-student")
        val teacherId = createActiveStudent("concurrency-submit-teacher")
        val requestId = createPublishedRequest(teacherId, studentId)

        val results = submitConcurrently(requestId, studentId, count = 2)

        assertThat(results.filter { it.isSuccess }).hasSize(2)
        assertThat(results.mapNotNull { it.exceptionOrNull() }).isEmpty()
        assertThat(
            portfolioSubmissionRepository.countByRequestIdAndStatus(
                requestId,
                PortfolioSubmissionStatus.SUBMITTED,
            ),
        ).isEqualTo(1)
    }

    private fun submitConcurrently(
        requestId: Long,
        studentMemberId: Long,
        count: Int,
    ): List<Result<Unit>> {
        val executor = Executors.newFixedThreadPool(count)
        val ready = CountDownLatch(count)
        val start = CountDownLatch(1)
        val results = ConcurrentLinkedQueue<Result<Unit>>()

        repeat(count) {
            executor.submit {
                ready.countDown()
                start.await()
                val result =
                    runCatching {
                        portfolioSubmissionService.submit(
                            requestId = requestId,
                            requesterId = studentMemberId,
                            request =
                                PortfolioSubmissionUpsertRequest(
                                    status = PortfolioSubmissionStatus.SUBMITTED,
                                    portfolioUrl = "https://portfolio.example.com/me",
                                ),
                        )
                        Unit
                    }
                results.add(result)
            }
        }
        ready.await()
        start.countDown()
        executor.shutdown()
        check(executor.awaitTermination(30, TimeUnit.SECONDS)) { "동시 제출 Thread가 제한 시간 안에 끝나지 않았습니다." }
        return results.toList()
    }

    private fun createPublishedRequest(
        teacherId: Long,
        studentId: Long,
    ): Long {
        val created =
            portfolioRequestService.create(
                PortfolioRequestCreateRequest(
                    title = "동시성 테스트 수합 요청",
                    description = "본문",
                    dueAt = LocalDateTime.now().plusDays(2),
                    targetStudentIds = listOf(studentId),
                ),
                teacherId,
            )
        portfolioRequestService.changeStatus(
            created.requestId,
            PortfolioRequestStatusUpdateRequest(status = PortfolioRequestStatus.PUBLISHED),
        )
        return created.requestId
    }

    private fun createActiveStudent(subject: String): Long {
        val member =
            memberRepository.saveAndFlush(
                Member(
                    oauthProvider = OAuthProvider.DG,
                    oauthSubject = subject,
                    email = "$subject@example.com",
                    status = MemberStatus.ACTIVE,
                ),
            )
        val memberId = requireNotNull(member.id)
        memberRoleRepository.saveAndFlush(MemberRole(MemberRoleId(memberId = memberId, role = RoleType.STUDENT)))
        return memberId
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
