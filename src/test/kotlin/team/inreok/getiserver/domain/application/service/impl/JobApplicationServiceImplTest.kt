package team.inreok.getiserver.domain.application.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.application.dto.ApplicationAnswer
import team.inreok.getiserver.domain.application.dto.CreateJobApplicationRequest
import team.inreok.getiserver.domain.application.dto.SaveJobApplicationDraftRequest
import team.inreok.getiserver.domain.application.entity.Form
import team.inreok.getiserver.domain.application.entity.JobApplication
import team.inreok.getiserver.domain.application.entity.JobApplicationForm
import team.inreok.getiserver.domain.application.entity.type.FormStatus
import team.inreok.getiserver.domain.application.entity.type.FormType
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus
import team.inreok.getiserver.domain.application.exception.ActiveApplicationExistsException
import team.inreok.getiserver.domain.application.exception.ApplicationAccessForbiddenException
import team.inreok.getiserver.domain.application.exception.ApplicationActionNotAvailableException
import team.inreok.getiserver.domain.application.exception.ApplicationNotFoundException
import team.inreok.getiserver.domain.application.exception.JobNotApplicableException
import team.inreok.getiserver.domain.application.repository.FormRepository
import team.inreok.getiserver.domain.application.repository.JobApplicationFormRepository
import team.inreok.getiserver.domain.application.repository.JobApplicationRepository
import team.inreok.getiserver.domain.application.service.JobApplicationService
import team.inreok.getiserver.domain.job.query.JobApplicationJobSnapshot
import team.inreok.getiserver.domain.job.query.JobApplicationSnapshotQueryPort
import team.inreok.getiserver.domain.member.query.MemberApplicantSnapshot
import team.inreok.getiserver.domain.member.query.MemberApplicantSnapshotQueryPort
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class JobApplicationServiceImplTest {
    @Mock
    private lateinit var jobApplicationRepository: JobApplicationRepository

    @Mock
    private lateinit var jobApplicationFormRepository: JobApplicationFormRepository

    @Mock
    private lateinit var formRepository: FormRepository

    @Mock
    private lateinit var jobApplicationSnapshotQueryPort: JobApplicationSnapshotQueryPort

    @Mock
    private lateinit var memberApplicantSnapshotQueryPort: MemberApplicantSnapshotQueryPort

    @Captor
    private lateinit var jobApplicationCaptor: ArgumentCaptor<JobApplication>

    private val service: JobApplicationService by lazy {
        JobApplicationServiceImpl(
            jobApplicationRepository,
            jobApplicationFormRepository,
            formRepository,
            jobApplicationSnapshotQueryPort,
            memberApplicantSnapshotQueryPort,
            JsonMapper(),
        )
    }

    private val fixedTime = LocalDateTime.of(2026, 3, 1, 10, 0, 0)

    private fun jobOf(
        status: String = "PUBLISHED",
        applicationMethod: String = "INTERNAL",
    ) = JobApplicationJobSnapshot(
        jobId = 1L,
        title = "인턴 채용",
        companyId = 1L,
        postingType = "MOU",
        applicationMethod = applicationMethod,
        status = status,
        targetGrade = null,
        recruitmentStartedAt = null,
        recruitmentEndedAt = null,
        createdByMemberId = 100L,
        managerMemberId = null,
    )

    private fun memberOf() =
        MemberApplicantSnapshot(
            memberId = 1L,
            name = "홍길동",
            email = "student@example.com",
            phone = "010-1234-5678",
            academicStatus = "ENROLLED",
            grade = 3,
            cohort = 10,
            department = "SW_DEVELOPMENT",
            majors = listOf("소프트웨어"),
            techStacks = listOf("Kotlin"),
            desiredJob = "Backend Developer",
        )

    private fun linkedActiveForm(id: Long = 10L) =
        Form(ownerMemberId = 200L, name = "지원서", formType = FormType.JOB, status = FormStatus.ACTIVE).apply {
            this.id = id
            currentVersion = 2
        }

    private fun stubActiveLink(formId: Long = 10L) {
        given(jobApplicationFormRepository.findById(1L))
            .willReturn(Optional.of(JobApplicationForm(jobId = 1L, formId = formId, linkedByMemberId = 200L)))
        given(formRepository.findById(formId)).willReturn(Optional.of(linkedActiveForm(formId)))
    }

    private fun anyJobApplication(): JobApplication =
        any(JobApplication::class.java)
            ?: JobApplication(jobId = 0, applicantMemberId = 0, attemptNumber = 1, contactEmail = "", answers = "[]")

    // ---------- createDraft ----------

    @Test
    fun `지원 가능하면 초안을 생성하고 prefillProfileFields면 프로필을 스냅샷한다`() {
        given(jobApplicationSnapshotQueryPort.findById(1L)).willReturn(jobOf())
        given(memberApplicantSnapshotQueryPort.findById(1L)).willReturn(memberOf())
        stubActiveLink()
        given(
            jobApplicationRepository.findByJobIdAndApplicantMemberIdAndStatusIn(
                1L,
                1L,
                ACTIVE_JOB_APPLICATION_STATUSES,
            ),
        ).willReturn(null)
        given(
            jobApplicationRepository.findTopByJobIdAndApplicantMemberIdOrderByAttemptNumberDesc(1L, 1L),
        ).willReturn(null)
        given(jobApplicationRepository.saveAndFlush(anyJobApplication())).willAnswer { invocation ->
            (invocation.arguments[0] as JobApplication).apply {
                id = 1L
                createdAt = fixedTime
                updatedAt = fixedTime
            }
        }

        val result = service.createDraft(1L, 1L, CreateJobApplicationRequest(prefillProfileFields = true))

        assertThat(result.applicationId).isEqualTo(1L)
        assertThat(result.status).isEqualTo(JobApplicationStatus.DRAFT)
        assertThat(result.formId).isEqualTo(10L)
        assertThat(result.formVersion).isEqualTo(2)
        assertThat(result.contactEmail).isEqualTo("student@example.com")
        assertThat(result.contactPhone).isEqualTo("010-1234-5678")
        assertThat(result.applicantName).isEqualTo("홍길동")
        assertThat(result.applicantMajors).containsExactly("소프트웨어")
        assertThat(result.applicantTechStacks).containsExactly("Kotlin")
    }

    @Test
    fun `prefillProfileFields가 false면 이메일 외 프로필은 채우지 않는다`() {
        given(jobApplicationSnapshotQueryPort.findById(1L)).willReturn(jobOf())
        given(memberApplicantSnapshotQueryPort.findById(1L)).willReturn(memberOf())
        stubActiveLink()
        given(
            jobApplicationRepository.findByJobIdAndApplicantMemberIdAndStatusIn(
                1L,
                1L,
                ACTIVE_JOB_APPLICATION_STATUSES,
            ),
        ).willReturn(null)
        given(
            jobApplicationRepository.findTopByJobIdAndApplicantMemberIdOrderByAttemptNumberDesc(1L, 1L),
        ).willReturn(null)
        given(jobApplicationRepository.saveAndFlush(anyJobApplication())).willAnswer { invocation ->
            (invocation.arguments[0] as JobApplication).apply {
                id = 1L
                createdAt = fixedTime
                updatedAt = fixedTime
            }
        }

        val result = service.createDraft(1L, 1L, CreateJobApplicationRequest(prefillProfileFields = false))

        assertThat(result.contactEmail).isEqualTo("student@example.com")
        assertThat(result.contactPhone).isNull()
        assertThat(result.applicantName).isNull()
        assertThat(result.applicantMajors).isEmpty()
    }

    @Test
    fun `취소 후 재지원이면 이전 attemptNumber 다음 값을 사용한다`() {
        given(jobApplicationSnapshotQueryPort.findById(1L)).willReturn(jobOf())
        given(memberApplicantSnapshotQueryPort.findById(1L)).willReturn(memberOf())
        stubActiveLink()
        given(
            jobApplicationRepository.findByJobIdAndApplicantMemberIdAndStatusIn(
                1L,
                1L,
                ACTIVE_JOB_APPLICATION_STATUSES,
            ),
        ).willReturn(null)
        given(jobApplicationRepository.findTopByJobIdAndApplicantMemberIdOrderByAttemptNumberDesc(1L, 1L))
            .willReturn(
                JobApplication(
                    jobId = 1L,
                    applicantMemberId = 1L,
                    attemptNumber = 1,
                    contactEmail = "x",
                    answers = "[]",
                ).apply { status = JobApplicationStatus.WITHDRAWN },
            )
        given(jobApplicationRepository.saveAndFlush(anyJobApplication())).willAnswer { invocation ->
            (invocation.arguments[0] as JobApplication).apply {
                id = 2L
                createdAt = fixedTime
                updatedAt = fixedTime
            }
        }

        service.createDraft(1L, 1L, CreateJobApplicationRequest())

        verify(jobApplicationRepository).saveAndFlush(jobApplicationCaptor.capture() ?: draftOf())
        assertThat(jobApplicationCaptor.value.attemptNumber).isEqualTo(2)
    }

    @Test
    fun `지원할 수 없는 공고면 JobNotApplicableException을 던진다`() {
        given(jobApplicationSnapshotQueryPort.findById(1L)).willReturn(jobOf(status = "DRAFT"))
        given(memberApplicantSnapshotQueryPort.findById(1L)).willReturn(memberOf())

        assertThatThrownBy { service.createDraft(1L, 1L, CreateJobApplicationRequest()) }
            .isInstanceOf(JobNotApplicableException::class.java)
    }

    @Test
    fun `이미 활성 지원서가 있으면 ActiveApplicationExistsException을 던진다`() {
        given(jobApplicationSnapshotQueryPort.findById(1L)).willReturn(jobOf())
        given(memberApplicantSnapshotQueryPort.findById(1L)).willReturn(memberOf())
        stubActiveLink()
        given(
            jobApplicationRepository.findByJobIdAndApplicantMemberIdAndStatusIn(
                1L,
                1L,
                ACTIVE_JOB_APPLICATION_STATUSES,
            ),
        ).willReturn(
            JobApplication(
                jobId = 1L,
                applicantMemberId = 1L,
                attemptNumber = 1,
                contactEmail = "x",
                answers = "[]",
            ),
        )

        assertThatThrownBy { service.createDraft(1L, 1L, CreateJobApplicationRequest()) }
            .isInstanceOf(ActiveApplicationExistsException::class.java)
    }

    // ---------- saveDraft ----------

    private fun draftOf(
        id: Long = 1L,
        applicantMemberId: Long = 1L,
        status: JobApplicationStatus = JobApplicationStatus.DRAFT,
    ) = JobApplication(
        jobId = 1L,
        applicantMemberId = applicantMemberId,
        attemptNumber = 1,
        contactEmail = "student@example.com",
        answers = "[]",
        status = status,
    ).apply {
        this.id = id
        createdAt = fixedTime
        updatedAt = fixedTime
    }

    @Test
    fun `DRAFT 지원서를 임시저장하면 전달한 Field만 반영된다`() {
        given(jobApplicationRepository.findById(1L)).willReturn(Optional.of(draftOf()))

        val result =
            service.saveDraft(
                1L,
                1L,
                SaveJobApplicationDraftRequest(
                    contactPhone = "010-0000-0000",
                    answers = listOf(ApplicationAnswer(fieldId = "motivation", value = null)),
                    privacyConsent = true,
                ),
            )

        assertThat(result.contactPhone).isEqualTo("010-0000-0000")
        assertThat(result.privacyConsent).isTrue()
        assertThat(result.answers).hasSize(1)
        assertThat(result.answers[0].fieldId).isEqualTo("motivation")
    }

    @Test
    fun `존재하지 않는 지원서를 임시저장하면 ApplicationNotFoundException을 던진다`() {
        given(jobApplicationRepository.findById(999L)).willReturn(Optional.empty())

        assertThatThrownBy { service.saveDraft(999L, 1L, SaveJobApplicationDraftRequest()) }
            .isInstanceOf(ApplicationNotFoundException::class.java)
    }

    @Test
    fun `다른 학생의 지원서를 임시저장하면 ApplicationAccessForbiddenException을 던진다`() {
        given(jobApplicationRepository.findById(1L)).willReturn(Optional.of(draftOf(applicantMemberId = 1L)))

        assertThatThrownBy { service.saveDraft(1L, 2L, SaveJobApplicationDraftRequest()) }
            .isInstanceOf(ApplicationAccessForbiddenException::class.java)
    }

    @Test
    fun `DRAFT가 아닌 지원서를 임시저장하면 ApplicationActionNotAvailableException을 던진다`() {
        given(
            jobApplicationRepository.findById(1L),
        ).willReturn(Optional.of(draftOf(status = JobApplicationStatus.SUBMITTED)))

        assertThatThrownBy { service.saveDraft(1L, 1L, SaveJobApplicationDraftRequest()) }
            .isInstanceOf(ApplicationActionNotAvailableException::class.java)
    }
}
