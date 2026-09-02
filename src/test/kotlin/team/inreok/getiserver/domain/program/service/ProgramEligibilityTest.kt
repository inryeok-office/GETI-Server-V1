package team.inreok.getiserver.domain.program.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import team.inreok.getiserver.domain.member.query.MemberApplicantSnapshot
import team.inreok.getiserver.domain.program.entity.Program
import team.inreok.getiserver.domain.program.entity.type.ProgramApplicationEligibilityReason
import team.inreok.getiserver.domain.program.entity.type.ProgramStatus
import team.inreok.getiserver.domain.program.entity.type.ProgramType
import java.time.LocalDateTime

class ProgramEligibilityTest {
    private val now = LocalDateTime.of(2026, 8, 5, 12, 0, 0)

    private fun programOf(
        status: ProgramStatus = ProgramStatus.PUBLISHED,
        applicationStartedAt: LocalDateTime? = now.minusDays(1),
        applicationEndedAt: LocalDateTime? = now.plusDays(1),
        capacity: Int? = null,
    ) = Program(
        createdByMemberId = 100L,
        type = ProgramType.SPECIAL_LECTURE,
        title = "2026 여름방학 백엔드 특강",
        status = status,
    ).apply {
        this.applicationStartedAt = applicationStartedAt
        this.applicationEndedAt = applicationEndedAt
        this.capacity = capacity
    }

    private fun memberOf(
        academicStatus: String? = "ENROLLED",
        grade: Int? = 3,
    ) = MemberApplicantSnapshot(
        memberId = 1L,
        name = "홍길동",
        email = "student@example.com",
        phone = null,
        academicStatus = academicStatus,
        grade = grade,
        cohort = 10,
        department = "SW_DEVELOPMENT",
        majors = emptyList(),
        techStacks = emptyList(),
        desiredJob = null,
    )

    @Test
    fun `모든 조건을 만족하면 AVAILABLE이다`() {
        val reason =
            computeProgramEligibilityReason(
                program = programOf(),
                targetGrades = setOf(2, 3),
                member = memberOf(),
                hasActiveApplication = false,
                currentApplicants = 0,
                now = now,
            )

        assertThat(reason).isEqualTo(ProgramApplicationEligibilityReason.AVAILABLE)
    }

    @Test
    fun `프로그램 상태가 PUBLISHED가 아니면 PROGRAM_NOT_PUBLISHED다`() {
        val reason =
            computeProgramEligibilityReason(
                program = programOf(status = ProgramStatus.DRAFT),
                targetGrades = emptySet(),
                member = memberOf(),
                hasActiveApplication = false,
                currentApplicants = 0,
                now = now,
            )

        assertThat(reason).isEqualTo(ProgramApplicationEligibilityReason.PROGRAM_NOT_PUBLISHED)
    }

    @Test
    fun `학생 정보가 없으면 NOT_ENROLLED다`() {
        val reason =
            computeProgramEligibilityReason(
                program = programOf(),
                targetGrades = emptySet(),
                member = null,
                hasActiveApplication = false,
                currentApplicants = 0,
                now = now,
            )

        assertThat(reason).isEqualTo(ProgramApplicationEligibilityReason.NOT_ENROLLED)
    }

    @Test
    fun `재학 상태가 아니면 NOT_ENROLLED다`() {
        val reason =
            computeProgramEligibilityReason(
                program = programOf(),
                targetGrades = emptySet(),
                member = memberOf(academicStatus = "GRADUATED"),
                hasActiveApplication = false,
                currentApplicants = 0,
                now = now,
            )

        assertThat(reason).isEqualTo(ProgramApplicationEligibilityReason.NOT_ENROLLED)
    }

    @Test
    fun `대상 학년이 아니면 NOT_TARGET_GRADE다`() {
        val reason =
            computeProgramEligibilityReason(
                program = programOf(),
                targetGrades = setOf(1, 2),
                member = memberOf(grade = 3),
                hasActiveApplication = false,
                currentApplicants = 0,
                now = now,
            )

        assertThat(reason).isEqualTo(ProgramApplicationEligibilityReason.NOT_TARGET_GRADE)
    }

    @Test
    fun `대상 학년이 비어있으면 학년 제한이 없다`() {
        val reason =
            computeProgramEligibilityReason(
                program = programOf(),
                targetGrades = emptySet(),
                member = memberOf(grade = 1),
                hasActiveApplication = false,
                currentApplicants = 0,
                now = now,
            )

        assertThat(reason).isEqualTo(ProgramApplicationEligibilityReason.AVAILABLE)
    }

    @Test
    fun `신청 시작 전이면 PROGRAM_NOT_OPEN이다`() {
        val reason =
            computeProgramEligibilityReason(
                program = programOf(applicationStartedAt = now.plusDays(1)),
                targetGrades = emptySet(),
                member = memberOf(),
                hasActiveApplication = false,
                currentApplicants = 0,
                now = now,
            )

        assertThat(reason).isEqualTo(ProgramApplicationEligibilityReason.PROGRAM_NOT_OPEN)
    }

    @Test
    fun `신청 종료 후면 PROGRAM_CLOSED다`() {
        val reason =
            computeProgramEligibilityReason(
                program = programOf(applicationEndedAt = now.minusDays(1)),
                targetGrades = emptySet(),
                member = memberOf(),
                hasActiveApplication = false,
                currentApplicants = 0,
                now = now,
            )

        assertThat(reason).isEqualTo(ProgramApplicationEligibilityReason.PROGRAM_CLOSED)
    }

    @Test
    fun `이미 활성 신청이 있으면 ALREADY_APPLIED다`() {
        val reason =
            computeProgramEligibilityReason(
                program = programOf(),
                targetGrades = emptySet(),
                member = memberOf(),
                hasActiveApplication = true,
                currentApplicants = 0,
                now = now,
            )

        assertThat(reason).isEqualTo(ProgramApplicationEligibilityReason.ALREADY_APPLIED)
    }

    @Test
    fun `정원이 가득 찼으면 PROGRAM_FULL이다`() {
        val reason =
            computeProgramEligibilityReason(
                program = programOf(capacity = 20),
                targetGrades = emptySet(),
                member = memberOf(),
                hasActiveApplication = false,
                currentApplicants = 20,
                now = now,
            )

        assertThat(reason).isEqualTo(ProgramApplicationEligibilityReason.PROGRAM_FULL)
    }

    // ProgramCloseScheduler(Program 자동 마감 Scheduler)가 신청 종료 시각이 지난 PUBLISHED
    // Program을 실제로 CLOSED로 전이하기 시작해(Phase 7) status=CLOSED인 Program이 존재할 수
    // 있다. computeProgramEligibilityReason이 최상단에서 CLOSED를 PROGRAM_NOT_PUBLISHED보다
    // 먼저 명시적으로 처리하는지 검증한다(PR #81 리뷰 MINOR 지적, ProgramEligibility.kt KDoc 참고).
    @Test
    fun `status가 CLOSED면 PROGRAM_CLOSED로 판정된다`() {
        val reason =
            computeProgramEligibilityReason(
                program = programOf(status = ProgramStatus.CLOSED),
                targetGrades = emptySet(),
                member = memberOf(),
                hasActiveApplication = false,
                currentApplicants = 0,
                now = now,
            )

        assertThat(reason).isEqualTo(ProgramApplicationEligibilityReason.PROGRAM_CLOSED)
    }

    @Test
    fun `정원이 없으면 인원 제한이 없다`() {
        val reason =
            computeProgramEligibilityReason(
                program = programOf(capacity = null),
                targetGrades = emptySet(),
                member = memberOf(),
                hasActiveApplication = false,
                currentApplicants = 9999,
                now = now,
            )

        assertThat(reason).isEqualTo(ProgramApplicationEligibilityReason.AVAILABLE)
    }
}
