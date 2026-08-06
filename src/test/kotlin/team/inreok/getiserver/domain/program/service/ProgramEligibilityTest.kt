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

    // PR #81 리뷰 MINOR 지적: status=CLOSED Program은 지금 이 Test처럼 PROGRAM_NOT_PUBLISHED로
    // 판정된다(computeProgramEligibilityReason의 최상단 `status != PUBLISHED` 분기가 먼저
    // 걸린다). 지금은 이게 실제로 안전하다 — Program.status를 CLOSED로 바꾸는 Scheduler가
    // 아직 없어(Phase 7 범위) status=CLOSED인 Program 자체가 생기지 않고, 신청 마감은 대신
    // applicationEndedAt 비교로 PROGRAM_CLOSED를 정확히 반환한다(위 "신청 종료 후면
    // PROGRAM_CLOSED다" Test 참고). Phase 7에서 Scheduler가 실제로 status=CLOSED를 설정하기
    // 시작하면 이 Test는 실패해야 정상이다 — 그때 computeProgramEligibilityReason의 분기
    // 순서를 조정해(CLOSED를 PROGRAM_NOT_PUBLISHED보다 먼저 명시적으로 처리) 이 Test의
    // 기대값을 PROGRAM_CLOSED로 함께 바꿔야 한다(ProgramEligibility.kt KDoc 경고 참고).
    @Test
    fun `status가 CLOSED면(Scheduler 도입 전) 아직 PROGRAM_NOT_PUBLISHED로 판정된다`() {
        val reason =
            computeProgramEligibilityReason(
                program = programOf(status = ProgramStatus.CLOSED),
                targetGrades = emptySet(),
                member = memberOf(),
                hasActiveApplication = false,
                currentApplicants = 0,
                now = now,
            )

        assertThat(reason).isEqualTo(ProgramApplicationEligibilityReason.PROGRAM_NOT_PUBLISHED)
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
