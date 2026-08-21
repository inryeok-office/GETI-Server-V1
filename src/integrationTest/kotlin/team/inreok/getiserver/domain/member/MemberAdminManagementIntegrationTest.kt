package team.inreok.getiserver.domain.member

import com.redis.testcontainers.RedisContainer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.data.domain.PageRequest
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.MemberRole
import team.inreok.getiserver.domain.member.entity.MemberRoleId
import team.inreok.getiserver.domain.member.entity.type.DepartmentType
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.exception.MemberStatusTransitionNotAllowedException
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.member.repository.MemberRoleRepository
import team.inreok.getiserver.domain.member.service.MemberAdminManagementService
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 관리자 회원 관리(Issue #183)의 JPQL Filter와 동시성을 실제 PostgreSQL로 검증한다. Mock 기반
 * [team.inreok.getiserver.domain.member.service.impl.MemberAdminManagementServiceImplTest]는
 * Repository를 Stub 처리해 실제 Query 실행과 DB Row Lock 자체를 재현할 수 없어 이 Test에서만 확인된다.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "app.jwt.secret=member-admin-management-integration-test-only-jwt-secret-value",
        "app.jwt.access-token-expiration-seconds=1800",
        "app.jwt.refresh-token-expiration-seconds=1209600",
        "app.file.storage.bucket=geti-integration-test",
        "app.file.storage.region=us-east-1",
        "app.file.storage.access-key=integration-test-only-access-key",
        "app.file.storage.secret-key=integration-test-only-secret-key",
    ],
)
class MemberAdminManagementIntegrationTest {
    @Autowired
    private lateinit var memberAdminManagementService: MemberAdminManagementService

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var memberRoleRepository: MemberRoleRepository

    // ---------- search Filter ----------

    @Test
    fun `name status role cohort department Filter가 모두 AND로 결합되어 실제 DB에서 걸러진다`() {
        val target =
            createMember(
                name = "Hong Gildong",
                status = MemberStatus.ACTIVE,
                cohort = 5,
                department = DepartmentType.AI,
                roles = setOf(RoleType.STUDENT),
            )
        // 이름만 일치(기수 불일치) -- 제외되어야 한다.
        createMember(
            name = "Hong Gilsu",
            status = MemberStatus.ACTIVE,
            cohort = 6,
            department = DepartmentType.AI,
            roles = setOf(RoleType.STUDENT),
        )
        // 기수·학과·이름 모두 일치하지만 status 불일치 -- 제외되어야 한다.
        createMember(
            name = "Hong Gilja",
            status = MemberStatus.SUSPENDED,
            cohort = 5,
            department = DepartmentType.AI,
            roles = setOf(RoleType.STUDENT),
        )

        val result =
            memberAdminManagementService.search(
                name = "gild",
                status = MemberStatus.ACTIVE,
                role = RoleType.STUDENT,
                cohort = 5,
                department = DepartmentType.AI,
                pageable = PageRequest.of(0, 20),
            )

        assertThat(result.content.map { it.memberId }).containsExactly(target)
    }

    @Test
    fun `role Filter는 EXISTS Subquery로 해당 Role을 가진 회원만 반환한다`() {
        // 이 Test Class의 다른 Test도 TEACHER Role을 가진 회원을 만들 수 있어(같은 Testcontainers
        // Postgres를 Class 전체가 공유하고 Test 사이에 Rollback하지 않는다, MemberApprovalIntegrationTest
        // 등 기존 관례와 동일), role=TEACHER 전역 검색은 다른 Test의 잔여 데이터와 섞일 수 있다.
        // cohort를 이 Test만의 고유 값으로 함께 필터링해 결과를 이 Test가 만든 데이터로 좁힌다.
        val uniqueCohort = (100_000..999_999).random()
        val teacher = createMember(status = MemberStatus.ACTIVE, cohort = uniqueCohort, roles = setOf(RoleType.TEACHER))
        createMember(status = MemberStatus.ACTIVE, cohort = uniqueCohort, roles = setOf(RoleType.STUDENT))

        val result =
            memberAdminManagementService.search(
                name = null,
                status = null,
                role = RoleType.TEACHER,
                cohort = uniqueCohort,
                department = null,
                pageable = PageRequest.of(0, 20),
            )

        assertThat(result.content.map { it.memberId }).containsExactly(teacher)
    }

    // ---------- Role Set 교체 ----------

    @Test
    fun `Role Set을 교체하면 기존 Role은 회수되고 새 Role이 부여된다`() {
        val memberId = createMember(status = MemberStatus.ACTIVE, roles = setOf(RoleType.STUDENT))

        val result =
            memberAdminManagementService.updateRoles(
                memberId,
                requesterMemberId = 999L,
                roles = setOf(RoleType.TEACHER),
            )

        assertThat(result.roles).containsExactly(RoleType.TEACHER)
        val savedRoles = memberRoleRepository.findAllByIdMemberId(memberId).map { it.id.role }
        assertThat(savedRoles).containsExactly(RoleType.TEACHER)
    }

    // ---------- 동시성 ----------

    @Test
    fun `같은 회원을 동시에 ACTIVE-SUSPENDED로 전이하면 하나만 성공하고 나머지는 409로 거부된다`() {
        val memberId = createMember(status = MemberStatus.ACTIVE, roles = setOf(RoleType.STUDENT))

        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val results = ConcurrentLinkedQueue<Result<MemberStatus>>()

        repeat(2) {
            executor.submit {
                ready.countDown()
                start.await()
                results.add(
                    runCatching {
                        memberAdminManagementService
                            .updateStatus(memberId, requesterMemberId = 999L, status = MemberStatus.SUSPENDED)
                            .status
                    },
                )
            }
        }
        ready.await()
        start.countDown()
        executor.shutdown()
        check(executor.awaitTermination(30, TimeUnit.SECONDS)) { "동시 처리 Thread가 제한 시간 안에 끝나지 않았습니다." }

        val outcomes = results.toList()
        val successes = outcomes.filter { it.isSuccess }
        val failures = outcomes.mapNotNull { it.exceptionOrNull() }
        // 하나만 성공(ACTIVE -> SUSPENDED)하고, 나머지는 Row Lock을 얻은 뒤 이미 SUSPENDED가 된 것을
        // 보고 허용되지 않는 전이(SUSPENDED -> SUSPENDED)로 거부된다.
        assertThat(successes).hasSize(1)
        assertThat(failures).hasSize(1)
        assertThat(failures.single()).isInstanceOf(MemberStatusTransitionNotAllowedException::class.java)

        val finalStatus = requireNotNull(memberRepository.findById(memberId).orElse(null)).status
        assertThat(finalStatus).isEqualTo(MemberStatus.SUSPENDED)
    }

    private fun createMember(
        name: String = "지원자 ${UUID.randomUUID()}",
        status: MemberStatus = MemberStatus.ACTIVE,
        cohort: Int? = null,
        department: DepartmentType? = null,
        roles: Set<RoleType> = emptySet(),
    ): Long {
        val member =
            memberRepository.saveAndFlush(
                Member(
                    oauthProvider = OAuthProvider.DG,
                    oauthSubject = "admin-mgmt-it-${UUID.randomUUID()}",
                    email = "admin-mgmt-it-${UUID.randomUUID()}@example.com",
                    status = status,
                ).apply {
                    this.name = name
                    this.cohort = cohort
                    this.department = department
                },
            )
        val memberId = requireNotNull(member.id)
        roles.forEach { memberRoleRepository.save(MemberRole(MemberRoleId(memberId = memberId, role = it))) }
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
