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
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.program.entity.Program
import team.inreok.getiserver.domain.program.entity.ProgramApplication
import team.inreok.getiserver.domain.program.entity.type.ProgramApplicationStatus
import team.inreok.getiserver.domain.program.entity.type.ProgramStatus
import team.inreok.getiserver.domain.program.entity.type.ProgramType
import team.inreok.getiserver.domain.program.repository.ProgramApplicationRepository
import team.inreok.getiserver.domain.program.repository.ProgramRepository
import team.inreok.getiserver.domain.program.scheduler.ProgramCloseScheduler
import team.inreok.getiserver.domain.program.service.ProgramCloseService
import java.time.LocalDateTime

/**
 * `ProgramCloseScheduler`가 실제 PostgreSQL에서 신청 종료 시각이 지난 PUBLISHED Program만
 * 골라 CLOSED로 전이하는지 검증한다(원본 요구사항 문서 8절, Phase 7). Service Test(Mock
 * Repository)는 `ProgramRepository.findExpiredPublishedIds` Query 자체를 재현할 수 없어 이
 * Test에서만 확인된다.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "app.jwt.secret=program-close-scheduler-integration-test-only-jwt-secret-value",
        "app.jwt.access-token-expiration-seconds=1800",
        "app.jwt.refresh-token-expiration-seconds=1209600",
        "app.file.storage.bucket=geti-integration-test",
        "app.file.storage.region=us-east-1",
        "app.file.storage.access-key=integration-test-only-access-key",
        "app.file.storage.secret-key=integration-test-only-secret-key",
    ],
)
class ProgramCloseSchedulerIntegrationTest {
    @Autowired
    private lateinit var programCloseScheduler: ProgramCloseScheduler

    @Autowired
    private lateinit var programCloseService: ProgramCloseService

    @Autowired
    private lateinit var programRepository: ProgramRepository

    @Autowired
    private lateinit var programApplicationRepository: ProgramApplicationRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Test
    fun `신청 종료 시각이 지난 PUBLISHED Program은 CLOSED로 전이되고, 남은 Program은 그대로다`() {
        val teacherId = createMember("close-scheduler-teacher")
        val now = LocalDateTime.now()

        val expiredId = createProgram(teacherId, ProgramStatus.PUBLISHED, applicationEndedAt = now.minusDays(1))
        val notExpiredId = createProgram(teacherId, ProgramStatus.PUBLISHED, applicationEndedAt = now.plusDays(1))
        val draftId = createProgram(teacherId, ProgramStatus.DRAFT, applicationEndedAt = now.minusDays(1))
        val deletedId = createProgram(teacherId, ProgramStatus.DELETED, applicationEndedAt = now.minusDays(1))

        programCloseScheduler.closeExpiredPrograms()

        assertThat(statusOf(expiredId)).isEqualTo(ProgramStatus.CLOSED)
        assertThat(statusOf(notExpiredId)).isEqualTo(ProgramStatus.PUBLISHED)
        assertThat(statusOf(draftId)).isEqualTo(ProgramStatus.DRAFT)
        assertThat(statusOf(deletedId)).isEqualTo(ProgramStatus.DELETED)
    }

    // ProgramEligibility.computeProgramEligibilityReason의 `now.isAfter(applicationEndedAt)`와
    // 동일한 경계다 -- applicationEndedAt과 정확히 같은 시각에는 아직 CLOSED가 아니어야 한다.
    // Scheduler는 자체 LocalDateTime.now()로 대상을 고르므로, 경계값을 직접 통제하기 위해
    // ProgramCloseService.closeIfExpired를 같은 now로 직접 호출한다.
    @Test
    fun `applicationEndedAt과 정확히 같은 시각에는 아직 전이하지 않는다`() {
        val teacherId = createMember("close-scheduler-boundary-teacher")
        val boundary = LocalDateTime.now().plusMinutes(5)
        val programId = createProgram(teacherId, ProgramStatus.PUBLISHED, applicationEndedAt = boundary)

        val closed = programCloseService.closeIfExpired(programId, boundary)

        assertThat(closed).isFalse()
        assertThat(statusOf(programId)).isEqualTo(ProgramStatus.PUBLISHED)
    }

    @Test
    fun `CLOSED로 전이해도 기존 ProgramApplication 데이터는 보존된다`() {
        val teacherId = createMember("close-scheduler-applicant-teacher")
        val studentId = createMember("close-scheduler-applicant-student")
        val now = LocalDateTime.now()
        val programId = createProgram(teacherId, ProgramStatus.PUBLISHED, applicationEndedAt = now.minusDays(1))
        val application =
            programApplicationRepository.saveAndFlush(
                ProgramApplication(
                    programId = programId,
                    applicantMemberId = studentId,
                    status = ProgramApplicationStatus.APPLIED,
                ),
            )

        programCloseScheduler.closeExpiredPrograms()

        assertThat(statusOf(programId)).isEqualTo(ProgramStatus.CLOSED)
        val preserved = programApplicationRepository.findById(requireNotNull(application.id)).orElseThrow()
        assertThat(preserved.status).isEqualTo(ProgramApplicationStatus.APPLIED)
        assertThat(preserved.applicantMemberId).isEqualTo(studentId)
    }

    private fun statusOf(programId: Long): ProgramStatus = programRepository.findById(programId).orElseThrow().status

    private fun createMember(subject: String): Long {
        val member =
            memberRepository.saveAndFlush(
                Member(
                    oauthProvider = OAuthProvider.DG,
                    oauthSubject = subject,
                    email = "$subject@example.com",
                ),
            )
        return requireNotNull(member.id)
    }

    private fun createProgram(
        createdByMemberId: Long,
        status: ProgramStatus,
        applicationEndedAt: LocalDateTime?,
    ): Long {
        val program =
            programRepository.saveAndFlush(
                Program(
                    createdByMemberId = createdByMemberId,
                    type = ProgramType.SPECIAL_LECTURE,
                    title = "자동 마감 Test 특강",
                    status = status,
                ).apply {
                    this.applicationEndedAt = applicationEndedAt
                    applicationStartedAt = applicationEndedAt?.minusDays(7)
                },
            )
        return requireNotNull(program.id)
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
