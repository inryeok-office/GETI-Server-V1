package team.inreok.getiserver.domain.application.access

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.application.entity.Form
import team.inreok.getiserver.domain.application.entity.JobApplication
import team.inreok.getiserver.domain.application.entity.JobApplicationForm
import team.inreok.getiserver.domain.application.entity.type.FormStatus
import team.inreok.getiserver.domain.application.entity.type.FormType
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus
import team.inreok.getiserver.domain.application.repository.FormRepository
import team.inreok.getiserver.domain.application.repository.JobApplicationFormRepository
import team.inreok.getiserver.domain.application.repository.JobApplicationRepository
import team.inreok.getiserver.domain.job.query.JobApplicationJobSnapshot
import team.inreok.getiserver.domain.job.query.JobApplicationSnapshotQueryPort
import team.inreok.getiserver.domain.member.query.MemberApplicantSnapshot
import team.inreok.getiserver.domain.member.query.MemberApplicantSnapshotQueryPort

@ExtendWith(MockitoExtension::class)
class JobApplicationEligibilityAccessorImplTest {
    @Mock
    private lateinit var jobApplicationSnapshotQueryPort: JobApplicationSnapshotQueryPort

    @Mock
    private lateinit var memberApplicantSnapshotQueryPort: MemberApplicantSnapshotQueryPort

    @Mock
    private lateinit var jobApplicationFormRepository: JobApplicationFormRepository

    @Mock
    private lateinit var formRepository: FormRepository

    @Mock
    private lateinit var jobApplicationRepository: JobApplicationRepository

    private val accessor: JobApplicationEligibilityAccessorImpl by lazy {
        JobApplicationEligibilityAccessorImpl(
            jobApplicationSnapshotQueryPort,
            memberApplicantSnapshotQueryPort,
            jobApplicationFormRepository,
            formRepository,
            jobApplicationRepository,
        )
    }

    private fun jobOf(
        jobId: Long = 1L,
        status: String = "PUBLISHED",
        targetGrade: Int? = null,
    ) = JobApplicationJobSnapshot(
        jobId = jobId,
        title = "인턴 채용",
        companyId = 1L,
        postingType = "MOU",
        applicationMethod = "INTERNAL",
        status = status,
        targetGrade = targetGrade,
        recruitmentStartedAt = null,
        recruitmentEndedAt = null,
        createdByMemberId = 100L,
        managerMemberId = null,
    )

    private fun memberOf(
        memberId: Long = 7L,
        academicStatus: String? = "ENROLLED",
        grade: Int? = 3,
    ) = MemberApplicantSnapshot(
        memberId = memberId,
        name = "학생",
        email = "student@example.com",
        phone = null,
        academicStatus = academicStatus,
        grade = grade,
        cohort = null,
        department = null,
        majors = emptyList(),
        techStacks = emptyList(),
        desiredJob = null,
    )

    private fun applicationOf(
        id: Long = 1L,
        jobId: Long = 1L,
        applicantMemberId: Long = 7L,
        status: JobApplicationStatus = JobApplicationStatus.SUBMITTED,
    ) = JobApplication(
        jobId = jobId,
        applicantMemberId = applicantMemberId,
        attemptNumber = 1,
        contactEmail = "student@example.com",
        answers = "[]",
        status = status,
    ).apply { this.id = id }

    private fun activeFormOf(id: Long = 5L) =
        Form(ownerMemberId = 100L, name = "지원서 양식", formType = FormType.JOB, status = FormStatus.ACTIVE).apply {
            this.id = id
        }

    private fun givenActiveLinkedForm(jobId: Long) {
        given(jobApplicationFormRepository.findAllById(setOf(jobId)))
            .willReturn(listOf(JobApplicationForm(jobId = jobId, formId = 5L, linkedByMemberId = 100L)))
        given(formRepository.findAllById(setOf(5L))).willReturn(listOf(activeFormOf()))
    }

    @Test
    fun `빈 jobIds를 넘기면 빈 Map을 반환하고 아무것도 조회하지 않는다`() {
        val result = accessor.findAllByJobIds(emptySet(), 7L)

        assertThat(result).isEmpty()
    }

    @Test
    fun `지원 가능하면 canApply=true이고 CREATE_DRAFT만 가능하다`() {
        given(jobApplicationSnapshotQueryPort.findAllByIds(setOf(1L))).willReturn(mapOf(1L to jobOf()))
        given(memberApplicantSnapshotQueryPort.findById(7L)).willReturn(memberOf())
        givenActiveLinkedForm(1L)
        given(jobApplicationRepository.findByJobIdInAndApplicantMemberIdAndStatusIn(setOf(1L), 7L, ACTIVE_STATUSES))
            .willReturn(emptyList())

        val result = accessor.findAllByJobIds(setOf(1L), 7L).getValue(1L)

        assertThat(result.canApply).isTrue()
        assertThat(result.eligibilityReason).isEqualTo("AVAILABLE")
        assertThat(result.applicationId).isNull()
        assertThat(result.applicationStatus).isNull()
        assertThat(result.availableActions).containsExactly("CREATE_DRAFT")
    }

    @Test
    fun `이미 활성 지원서가 있으면 ALREADY_APPLIED이고 지원서 상태 기준 Action을 담는다`() {
        given(jobApplicationSnapshotQueryPort.findAllByIds(setOf(1L))).willReturn(mapOf(1L to jobOf()))
        given(memberApplicantSnapshotQueryPort.findById(7L)).willReturn(memberOf())
        givenActiveLinkedForm(1L)
        val activeApplication = applicationOf(id = 42L, status = JobApplicationStatus.SUBMITTED)
        given(jobApplicationRepository.findByJobIdInAndApplicantMemberIdAndStatusIn(setOf(1L), 7L, ACTIVE_STATUSES))
            .willReturn(listOf(activeApplication))

        val result = accessor.findAllByJobIds(setOf(1L), 7L).getValue(1L)

        assertThat(result.canApply).isFalse()
        assertThat(result.eligibilityReason).isEqualTo("ALREADY_APPLIED")
        assertThat(result.applicationId).isEqualTo(42L)
        assertThat(result.applicationStatus).isEqualTo("SUBMITTED")
        // SUBMITTED에서 학생이 할 수 있는 Action은 REQUEST_EDIT/WITHDRAW뿐이다
        // (JobApplicationServiceImpl.JOB_APPLICATION_ACTION_ALLOWED_FROM).
        assertThat(result.availableActions).containsExactlyInAnyOrder("REQUEST_EDIT", "WITHDRAW")
    }

    @Test
    fun `학생이 아니면(academicStatus 없음) NOT_ENROLLED이고 지원할 수 없다`() {
        given(jobApplicationSnapshotQueryPort.findAllByIds(setOf(1L))).willReturn(mapOf(1L to jobOf()))
        given(
            memberApplicantSnapshotQueryPort.findById(200L),
        ).willReturn(memberOf(memberId = 200L, academicStatus = null))
        givenActiveLinkedForm(1L)
        given(jobApplicationRepository.findByJobIdInAndApplicantMemberIdAndStatusIn(setOf(1L), 200L, ACTIVE_STATUSES))
            .willReturn(emptyList())

        val result = accessor.findAllByJobIds(setOf(1L), 200L).getValue(1L)

        assertThat(result.canApply).isFalse()
        assertThat(result.eligibilityReason).isEqualTo("NOT_ENROLLED")
        assertThat(result.applicationId).isNull()
        assertThat(result.availableActions).isEmpty()
    }

    @Test
    fun `여러 jobId를 한 번에 배치로 계산하고 결과 Map에 모두 담는다`() {
        given(jobApplicationSnapshotQueryPort.findAllByIds(setOf(1L, 2L)))
            .willReturn(mapOf(1L to jobOf(jobId = 1L), 2L to jobOf(jobId = 2L)))
        given(memberApplicantSnapshotQueryPort.findById(7L)).willReturn(memberOf())
        given(jobApplicationFormRepository.findAllById(setOf(1L, 2L)))
            .willReturn(
                listOf(
                    JobApplicationForm(jobId = 1L, formId = 5L, linkedByMemberId = 100L),
                    JobApplicationForm(jobId = 2L, formId = 5L, linkedByMemberId = 100L),
                ),
            )
        given(formRepository.findAllById(setOf(5L))).willReturn(listOf(activeFormOf()))
        given(jobApplicationRepository.findByJobIdInAndApplicantMemberIdAndStatusIn(setOf(1L, 2L), 7L, ACTIVE_STATUSES))
            .willReturn(emptyList())

        val result = accessor.findAllByJobIds(setOf(1L, 2L), 7L)

        assertThat(result).hasSize(2)
        assertThat(result.getValue(1L).canApply).isTrue()
        assertThat(result.getValue(2L).canApply).isTrue()
    }

    @Test
    fun `공고를 찾을 수 없으면 JOB_NOT_PUBLISHED로 지원할 수 없다`() {
        given(jobApplicationSnapshotQueryPort.findAllByIds(setOf(999L))).willReturn(emptyMap())
        given(memberApplicantSnapshotQueryPort.findById(7L)).willReturn(memberOf())
        given(jobApplicationFormRepository.findAllById(setOf(999L))).willReturn(emptyList())
        given(jobApplicationRepository.findByJobIdInAndApplicantMemberIdAndStatusIn(setOf(999L), 7L, ACTIVE_STATUSES))
            .willReturn(emptyList())

        val result = accessor.findAllByJobIds(setOf(999L), 7L).getValue(999L)

        assertThat(result.canApply).isFalse()
        assertThat(result.eligibilityReason).isEqualTo("JOB_NOT_PUBLISHED")
        assertThat(result.availableActions).isEmpty()
    }

    private companion object {
        val ACTIVE_STATUSES =
            setOf(
                JobApplicationStatus.DRAFT,
                JobApplicationStatus.SUBMITTED,
                JobApplicationStatus.EDIT_REQUESTED,
                JobApplicationStatus.EDIT_ALLOWED,
                JobApplicationStatus.REVISION_REQUESTED,
                JobApplicationStatus.APPROVED,
            )
    }
}
