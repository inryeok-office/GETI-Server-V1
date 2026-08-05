package team.inreok.getiserver.domain.application.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import team.inreok.getiserver.domain.application.entity.type.JobApplicationEligibilityReason
import team.inreok.getiserver.domain.job.query.JobApplicationJobSnapshot
import team.inreok.getiserver.domain.member.query.MemberApplicantSnapshot
import java.time.LocalDateTime

class JobApplicationEligibilityTest {
    private val now = LocalDateTime.of(2026, 8, 5, 12, 0, 0)

    private fun jobOf(
        status: String = "PUBLISHED",
        applicationMethod: String = "INTERNAL",
        targetGrade: Int? = null,
        recruitmentStartedAt: LocalDateTime? = null,
        recruitmentEndedAt: LocalDateTime? = null,
    ) = JobApplicationJobSnapshot(
        jobId = 1L,
        title = "2026 하계 인턴",
        companyId = 1L,
        postingType = "MOU",
        applicationMethod = applicationMethod,
        status = status,
        targetGrade = targetGrade,
        recruitmentStartedAt = recruitmentStartedAt,
        recruitmentEndedAt = recruitmentEndedAt,
        createdByMemberId = 100L,
        managerMemberId = null,
    )

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
            computeEligibilityReason(
                job = jobOf(),
                member = memberOf(),
                hasActiveLinkedForm = true,
                hasActiveApplication = false,
                now = now,
            )

        assertThat(reason).isEqualTo(JobApplicationEligibilityReason.AVAILABLE)
    }

    @Test
    fun `공고가 없으면 JOB_NOT_PUBLISHED다`() {
        val reason =
            computeEligibilityReason(
                job = null,
                member = memberOf(),
                hasActiveLinkedForm = true,
                hasActiveApplication = false,
                now = now,
            )

        assertThat(reason).isEqualTo(JobApplicationEligibilityReason.JOB_NOT_PUBLISHED)
    }

    @Test
    fun `공고 상태가 PUBLISHED가 아니면 JOB_NOT_PUBLISHED다`() {
        val reason =
            computeEligibilityReason(
                job = jobOf(status = "DRAFT"),
                member = memberOf(),
                hasActiveLinkedForm = true,
                hasActiveApplication = false,
                now = now,
            )

        assertThat(reason).isEqualTo(JobApplicationEligibilityReason.JOB_NOT_PUBLISHED)
    }

    @Test
    fun `지원 방식이 INTERNAL이 아니면 NOT_INTERNAL이다`() {
        val reason =
            computeEligibilityReason(
                job = jobOf(applicationMethod = "EXTERNAL"),
                member = memberOf(),
                hasActiveLinkedForm = true,
                hasActiveApplication = false,
                now = now,
            )

        assertThat(reason).isEqualTo(JobApplicationEligibilityReason.NOT_INTERNAL)
    }

    @Test
    fun `학생 정보가 없으면 NOT_ENROLLED다`() {
        val reason =
            computeEligibilityReason(
                job = jobOf(),
                member = null,
                hasActiveLinkedForm = true,
                hasActiveApplication = false,
                now = now,
            )

        assertThat(reason).isEqualTo(JobApplicationEligibilityReason.NOT_ENROLLED)
    }

    @Test
    fun `재학 상태가 아니면 NOT_ENROLLED다`() {
        val reason =
            computeEligibilityReason(
                job = jobOf(),
                member = memberOf(academicStatus = "GRADUATED"),
                hasActiveLinkedForm = true,
                hasActiveApplication = false,
                now = now,
            )

        assertThat(reason).isEqualTo(JobApplicationEligibilityReason.NOT_ENROLLED)
    }

    @Test
    fun `대상 학년이 아니면 NOT_TARGET_GRADE다`() {
        val reason =
            computeEligibilityReason(
                job = jobOf(targetGrade = 2),
                member = memberOf(grade = 3),
                hasActiveLinkedForm = true,
                hasActiveApplication = false,
                now = now,
            )

        assertThat(reason).isEqualTo(JobApplicationEligibilityReason.NOT_TARGET_GRADE)
    }

    @Test
    fun `targetGrade가 null이면 학년 제한이 없다`() {
        val reason =
            computeEligibilityReason(
                job = jobOf(targetGrade = null),
                member = memberOf(grade = 1),
                hasActiveLinkedForm = true,
                hasActiveApplication = false,
                now = now,
            )

        assertThat(reason).isEqualTo(JobApplicationEligibilityReason.AVAILABLE)
    }

    @Test
    fun `모집 시작 전이면 BEFORE_START다`() {
        val reason =
            computeEligibilityReason(
                job = jobOf(recruitmentStartedAt = now.plusDays(1)),
                member = memberOf(),
                hasActiveLinkedForm = true,
                hasActiveApplication = false,
                now = now,
            )

        assertThat(reason).isEqualTo(JobApplicationEligibilityReason.BEFORE_START)
    }

    @Test
    fun `모집 종료 후면 AFTER_END다`() {
        val reason =
            computeEligibilityReason(
                job = jobOf(recruitmentEndedAt = now.minusDays(1)),
                member = memberOf(),
                hasActiveLinkedForm = true,
                hasActiveApplication = false,
                now = now,
            )

        assertThat(reason).isEqualTo(JobApplicationEligibilityReason.AFTER_END)
    }

    @Test
    fun `활성 양식이 연결되어 있지 않으면 JOB_NOT_PUBLISHED다`() {
        val reason =
            computeEligibilityReason(
                job = jobOf(),
                member = memberOf(),
                hasActiveLinkedForm = false,
                hasActiveApplication = false,
                now = now,
            )

        assertThat(reason).isEqualTo(JobApplicationEligibilityReason.JOB_NOT_PUBLISHED)
    }

    @Test
    fun `이미 활성 지원서가 있으면 ALREADY_APPLIED다`() {
        val reason =
            computeEligibilityReason(
                job = jobOf(),
                member = memberOf(),
                hasActiveLinkedForm = true,
                hasActiveApplication = true,
                now = now,
            )

        assertThat(reason).isEqualTo(JobApplicationEligibilityReason.ALREADY_APPLIED)
    }
}
