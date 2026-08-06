package team.inreok.getiserver.domain.program

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
import team.inreok.getiserver.domain.member.entity.type.AcademicStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.program.dto.ProgramApplicationActionRequest
import team.inreok.getiserver.domain.program.dto.ProgramCreateRequest
import team.inreok.getiserver.domain.program.entity.type.ProgramApplicationAction
import team.inreok.getiserver.domain.program.entity.type.ProgramApplicationStatus
import team.inreok.getiserver.domain.program.entity.type.ProgramStatus
import team.inreok.getiserver.domain.program.entity.type.ProgramType
import team.inreok.getiserver.domain.program.exception.AlreadyAppliedException
import team.inreok.getiserver.domain.program.exception.ProgramFullException
import team.inreok.getiserver.domain.program.repository.ProgramApplicationRepository
import team.inreok.getiserver.domain.program.service.ProgramService
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Program 신청(APPLY)의 정원 동시성을 실제 PostgreSQL Pessimistic Lock으로 검증한다(원본 요구사항
 * 문서 "GETI-Server Program 도메인 전체 개발 요구사항" 11절/22절). Service Test(Mock Repository)는
 * DB Row Lock 자체를 재현할 수 없어 이 Test에서만 확인된다.
 *
 * `ProgramServiceImpl.apply()`가 `ProgramRepository.findByIdForUpdate`로 Program Row에
 * `PESSIMISTIC_WRITE` Lock을 건 뒤 정원을 확인·저장하므로, 같은 Program에 대한 동시 신청은 실제
 * DB 차원에서 순차적으로 처리된다.
 */
@Testcontainers
@SpringBootTest(
    // WebEnvironment.NONE은 Servlet Web Context를 만들지 않아 SecurityConfig의 HttpSecurity Bean을
    // 구성할 수 없다(HealthReadinessIntegrationTest와 동일하게 MOCK을 사용, 실제 HTTP 요청은
    // 보내지 않고 Service Bean만 사용한다).
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "app.jwt.secret=program-concurrency-integration-test-only-jwt-secret-value",
        "app.jwt.access-token-expiration-seconds=1800",
        "app.jwt.refresh-token-expiration-seconds=1209600",
    ],
)
class ProgramApplicationConcurrencyIntegrationTest {
    @Autowired
    private lateinit var programService: ProgramService

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var programApplicationRepository: ProgramApplicationRepository

    @Test
    fun `정원 1명인 프로그램에 서로 다른 학생 2명이 동시에 신청하면 한 명만 성공한다`() {
        val programId = createPublishedProgram(capacity = 1, teacherSubject = "concurrency-teacher-full")
        val studentAId = createEnrolledStudent("concurrency-full-a")
        val studentBId = createEnrolledStudent("concurrency-full-b")

        val results = applyConcurrently(programId, listOf(studentAId, studentBId))

        val successes = results.filter { it.isSuccess }
        val failures = results.mapNotNull { it.exceptionOrNull() }
        assertThat(successes).hasSize(1)
        assertThat(failures).hasSize(1)
        assertThat(failures.single()).isInstanceOf(ProgramFullException::class.java)
        assertThat(activeApplicantCount(programId)).isEqualTo(1)
    }

    @Test
    fun `같은 학생이 동시에 두 번 신청해도 활성 신청은 하나만 생긴다`() {
        val programId = createPublishedProgram(capacity = 10, teacherSubject = "concurrency-teacher-duplicate")
        val studentId = createEnrolledStudent("concurrency-duplicate")

        val results = applyConcurrently(programId, listOf(studentId, studentId))

        val successes = results.filter { it.isSuccess }
        val failures = results.mapNotNull { it.exceptionOrNull() }
        assertThat(successes).hasSize(1)
        assertThat(failures).hasSize(1)
        // Program Row Lock이 두 요청을 순차적으로 만들므로 두 번째 요청은 Eligibility 계산
        // 단계에서 이미 ALREADY_APPLIED로 걸러진다(uk_program_applications_active_singleton까지
        // 갈 필요가 없다). DB 제약은 Lock을 우회하는 예외 상황에 대한 최종 방어선이다.
        assertThat(failures.single()).isInstanceOf(AlreadyAppliedException::class.java)
        assertThat(activeApplicantCount(programId)).isEqualTo(1)
    }

    private fun applyConcurrently(
        programId: Long,
        studentMemberIds: List<Long>,
    ): List<Result<Unit>> {
        val executor = Executors.newFixedThreadPool(studentMemberIds.size)
        val ready = CountDownLatch(studentMemberIds.size)
        val start = CountDownLatch(1)
        val results = ConcurrentLinkedQueue<Result<Unit>>()

        studentMemberIds.forEach { studentId ->
            executor.submit {
                ready.countDown()
                start.await()
                val result =
                    runCatching {
                        programService.executeApplicationAction(
                            programId = programId,
                            studentMemberId = studentId,
                            request = ProgramApplicationActionRequest(action = ProgramApplicationAction.APPLY),
                        )
                        Unit
                    }
                results.add(result)
            }
        }
        ready.await()
        start.countDown()
        executor.shutdown()
        check(executor.awaitTermination(30, TimeUnit.SECONDS)) { "동시 신청 Thread가 제한 시간 안에 끝나지 않았습니다." }
        return results.toList()
    }

    private fun activeApplicantCount(programId: Long): Long =
        programApplicationRepository.countByProgramIdAndStatus(programId, ProgramApplicationStatus.APPLIED)

    private fun createPublishedProgram(
        capacity: Int,
        teacherSubject: String,
    ): Long {
        val teacherId = createEnrolledStudent(teacherSubject, academicStatus = null)
        val now = LocalDateTime.now()
        val response =
            programService.create(
                ProgramCreateRequest(
                    title = "동시성 테스트 특강",
                    programType = ProgramType.SPECIAL_LECTURE,
                    status = ProgramStatus.PUBLISHED,
                    content = "본문",
                    startAt = now.plusDays(3),
                    endAt = now.plusDays(3).plusHours(2),
                    applicationStartAt = now.minusDays(1),
                    applicationEndAt = now.plusDays(2),
                    location = "본관 대강당",
                    targetGrades = listOf(1, 2, 3),
                    capacity = capacity,
                    discordChannelId = "concurrency-test-channel",
                ),
                teacherId,
            )
        return response.programId
    }

    private fun createEnrolledStudent(
        subject: String,
        academicStatus: AcademicStatus? = AcademicStatus.ENROLLED,
    ): Long {
        val member =
            memberRepository.saveAndFlush(
                Member(
                    oauthProvider = OAuthProvider.DG,
                    oauthSubject = subject,
                    email = "$subject@example.com",
                ).apply {
                    this.academicStatus = academicStatus
                    grade = 2
                },
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
