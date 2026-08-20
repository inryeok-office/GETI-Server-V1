package team.inreok.getiserver.domain.application.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.dao.DataIntegrityViolationException
import team.inreok.getiserver.domain.application.dto.ApplicationAnswer
import team.inreok.getiserver.domain.application.dto.CreateJobApplicationRequest
import team.inreok.getiserver.domain.application.dto.FormFieldSchema
import team.inreok.getiserver.domain.application.dto.JobApplicationAction
import team.inreok.getiserver.domain.application.dto.JobApplicationActionRequest
import team.inreok.getiserver.domain.application.dto.SaveJobApplicationDraftRequest
import team.inreok.getiserver.domain.application.entity.Form
import team.inreok.getiserver.domain.application.entity.FormVersion
import team.inreok.getiserver.domain.application.entity.JobApplication
import team.inreok.getiserver.domain.application.entity.JobApplicationForm
import team.inreok.getiserver.domain.application.entity.JobApplicationStatusHistory
import team.inreok.getiserver.domain.application.entity.JobApplicationSubmission
import team.inreok.getiserver.domain.application.entity.type.FormFieldType
import team.inreok.getiserver.domain.application.entity.type.FormStatus
import team.inreok.getiserver.domain.application.entity.type.FormType
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus
import team.inreok.getiserver.domain.application.exception.ActiveApplicationExistsException
import team.inreok.getiserver.domain.application.exception.ApplicationAccessForbiddenException
import team.inreok.getiserver.domain.application.exception.ApplicationActionNotAvailableException
import team.inreok.getiserver.domain.application.exception.ApplicationNotFoundException
import team.inreok.getiserver.domain.application.exception.ApplicationRequiredAnswerMissingException
import team.inreok.getiserver.domain.application.exception.JobNotApplicableException
import team.inreok.getiserver.domain.application.repository.FormRepository
import team.inreok.getiserver.domain.application.repository.FormVersionRepository
import team.inreok.getiserver.domain.application.repository.JobApplicationFormRepository
import team.inreok.getiserver.domain.application.repository.JobApplicationRepository
import team.inreok.getiserver.domain.application.repository.JobApplicationStatusHistoryRepository
import team.inreok.getiserver.domain.application.repository.JobApplicationSubmissionRepository
import team.inreok.getiserver.domain.application.service.JobApplicationService
import team.inreok.getiserver.domain.company.query.CompanyQuery
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.link.FileLinkPort
import team.inreok.getiserver.domain.file.link.FileSnapshot
import team.inreok.getiserver.domain.job.access.JobBookmarkAccessor
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
    private lateinit var formVersionRepository: FormVersionRepository

    @Mock
    private lateinit var jobApplicationSnapshotQueryPort: JobApplicationSnapshotQueryPort

    @Mock
    private lateinit var memberApplicantSnapshotQueryPort: MemberApplicantSnapshotQueryPort

    @Mock
    private lateinit var jobApplicationStatusHistoryRepository: JobApplicationStatusHistoryRepository

    @Mock
    private lateinit var jobApplicationSubmissionRepository: JobApplicationSubmissionRepository

    @Mock
    private lateinit var fileLinkPort: FileLinkPort

    @Mock
    private lateinit var companyQuery: CompanyQuery

    @Mock
    private lateinit var jobBookmarkAccessor: JobBookmarkAccessor

    @Captor
    private lateinit var jobApplicationCaptor: ArgumentCaptor<JobApplication>

    private val jsonMapper = JsonMapper()

    private val service: JobApplicationService by lazy {
        JobApplicationServiceImpl(
            jobApplicationRepository,
            jobApplicationFormRepository,
            formRepository,
            formVersionRepository,
            jobApplicationSnapshotQueryPort,
            memberApplicantSnapshotQueryPort,
            jobApplicationStatusHistoryRepository,
            jobApplicationSubmissionRepository,
            fileLinkPort,
            companyQuery,
            jobBookmarkAccessor,
            jsonMapper,
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
        ).willReturn(emptyList())
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
        // 방금 생성한 초안은 애초에 연결된 파일이 있을 수 없어 File 도메인을 조회하지 않는다
        // (Issue #134, toJobApplicationDraftResponse KDoc 참고).
        assertThat(result.files).isEmpty()
        verify(fileLinkPort, never()).linkedFilesOf(anyFileOwnerType(), anyLong())
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
        ).willReturn(emptyList())
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
        ).willReturn(emptyList())
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
            listOf(
                JobApplication(
                    jobId = 1L,
                    applicantMemberId = 1L,
                    attemptNumber = 1,
                    contactEmail = "x",
                    answers = "[]",
                ),
            ),
        )

        assertThatThrownBy { service.createDraft(1L, 1L, CreateJobApplicationRequest()) }
            .isInstanceOf(ActiveApplicationExistsException::class.java)
    }

    @Test
    fun `동시 요청으로 DB Unique 제약을 위반하면 ActiveApplicationExistsException으로 변환한다`() {
        // uk_job_applications_active_singleton(V13 Migration)이 hasActiveApplication() 확인과
        // saveAndFlush() 사이의 TOCTOU 경합을 막는 최종 방어선이다(PR #79 Review 반영).
        given(jobApplicationSnapshotQueryPort.findById(1L)).willReturn(jobOf())
        given(memberApplicantSnapshotQueryPort.findById(1L)).willReturn(memberOf())
        stubActiveLink()
        given(
            jobApplicationRepository.findByJobIdAndApplicantMemberIdAndStatusIn(
                1L,
                1L,
                ACTIVE_JOB_APPLICATION_STATUSES,
            ),
        ).willReturn(emptyList())
        given(
            jobApplicationRepository.findTopByJobIdAndApplicantMemberIdOrderByAttemptNumberDesc(1L, 1L),
        ).willReturn(null)
        given(jobApplicationRepository.saveAndFlush(anyJobApplication()))
            .willThrow(DataIntegrityViolationException("uk_job_applications_active_singleton"))

        assertThatThrownBy { service.createDraft(1L, 1L, CreateJobApplicationRequest()) }
            .isInstanceOf(ActiveApplicationExistsException::class.java)
    }

    // ---------- saveDraft ----------

    private fun draftOf(
        id: Long = 1L,
        applicantMemberId: Long = 1L,
        status: JobApplicationStatus = JobApplicationStatus.DRAFT,
        formId: Long? = null,
        formVersion: Int? = null,
        answers: String = "[]",
        statusReason: String? = null,
    ) = JobApplication(
        jobId = 1L,
        applicantMemberId = applicantMemberId,
        attemptNumber = 1,
        contactEmail = "student@example.com",
        answers = answers,
        status = status,
    ).apply {
        this.id = id
        this.formId = formId
        this.formVersion = formVersion
        this.statusReason = statusReason
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
    fun `임시저장이 허용되지 않는 상태면 ApplicationActionNotAvailableException을 던진다`() {
        given(
            jobApplicationRepository.findById(1L),
        ).willReturn(Optional.of(draftOf(status = JobApplicationStatus.SUBMITTED)))

        assertThatThrownBy { service.saveDraft(1L, 1L, SaveJobApplicationDraftRequest()) }
            .isInstanceOf(ApplicationActionNotAvailableException::class.java)
    }

    @Test
    fun `EDIT_ALLOWED REVISION_REQUESTED 상태의 지원서도 임시저장할 수 있다`() {
        given(jobApplicationRepository.findById(1L))
            .willReturn(Optional.of(draftOf(status = JobApplicationStatus.EDIT_ALLOWED)))
        given(jobApplicationRepository.findById(2L))
            .willReturn(Optional.of(draftOf(id = 2L, status = JobApplicationStatus.REVISION_REQUESTED)))

        assertThat(
            service.saveDraft(1L, 1L, SaveJobApplicationDraftRequest(contactPhone = "010-1111-2222")).contactPhone,
        ).isEqualTo("010-1111-2222")
        assertThat(
            service.saveDraft(2L, 1L, SaveJobApplicationDraftRequest(contactPhone = "010-3333-4444")).contactPhone,
        ).isEqualTo("010-3333-4444")
    }

    // ---------- executeAction ----------

    private fun formVersionOf(
        formId: Long = 10L,
        version: Int = 1,
        requiredKeys: List<String> = listOf("motivation"),
    ) = FormVersion(
        formId = formId,
        version = version,
        schemaData =
            jsonMapper.writeValueAsString(
                requiredKeys.map { key ->
                    FormFieldSchema(
                        key = key,
                        type = FormFieldType.TEXT,
                        label = key,
                        description = null,
                        required = true,
                        order = 0,
                        options = null,
                        filePolicy = null,
                    )
                },
            ),
    )

    private fun answersJsonOf(fieldId: String) =
        jsonMapper.writeValueAsString(
            listOf(ApplicationAnswer(fieldId = fieldId, value = jsonMapper.readTree("\"답변\""))),
        )

    @Test
    fun `DRAFT 지원서에 필수 답변이 채워져 있으면 SUBMIT하면 SUBMITTED로 전이하고 submittedAt을 기록한다`() {
        val application =
            draftOf(formId = 10L, formVersion = 1, answers = answersJsonOf("motivation"))
        given(jobApplicationRepository.findByIdForUpdate(1L)).willReturn(application)
        given(formVersionRepository.findByFormIdAndVersion(10L, 1)).willReturn(formVersionOf())

        val result = service.executeAction(1L, 1L, JobApplicationActionRequest(JobApplicationAction.SUBMIT))

        assertThat(result.status).isEqualTo(JobApplicationStatus.SUBMITTED)
        assertThat(result.submittedAt).isNotNull()
    }

    @Test
    fun `SUBMIT하면 상태 이력과 제출 Snapshot을 같은 Transaction에서 함께 기록한다`() {
        val application =
            draftOf(formId = 10L, formVersion = 1, answers = answersJsonOf("motivation"))
        given(jobApplicationRepository.findByIdForUpdate(1L)).willReturn(application)
        given(formVersionRepository.findByFormIdAndVersion(10L, 1)).willReturn(formVersionOf())
        given(jobApplicationSubmissionRepository.findTopByApplicationIdOrderBySubmissionNumberDesc(1L))
            .willReturn(null)

        service.executeAction(1L, 1L, JobApplicationActionRequest(JobApplicationAction.SUBMIT))

        val historyCaptor = ArgumentCaptor.forClass(JobApplicationStatusHistory::class.java)
        verify(jobApplicationStatusHistoryRepository).save(historyCaptor.capture())
        assertThat(historyCaptor.value.fromStatus).isEqualTo(JobApplicationStatus.DRAFT)
        assertThat(historyCaptor.value.toStatus).isEqualTo(JobApplicationStatus.SUBMITTED)
        assertThat(historyCaptor.value.action).isEqualTo("SUBMIT")
        assertThat(historyCaptor.value.actorMemberId).isEqualTo(1L)
        assertThat(historyCaptor.value.reason).isNull()

        val submissionCaptor = ArgumentCaptor.forClass(JobApplicationSubmission::class.java)
        verify(jobApplicationSubmissionRepository).save(submissionCaptor.capture())
        assertThat(submissionCaptor.value.submissionNumber).isEqualTo(1)
        assertThat(submissionCaptor.value.formId).isEqualTo(10L)
        assertThat(submissionCaptor.value.formVersion).isEqualTo(1)
        assertThat(submissionCaptor.value.answers).isEqualTo(application.answers)
    }

    @Test
    fun `재제출이면 이전 제출 Snapshot 다음 submissionNumber로 기록한다`() {
        val application =
            draftOf(
                status = JobApplicationStatus.REVISION_REQUESTED,
                formId = 10L,
                formVersion = 1,
                answers = answersJsonOf("motivation"),
            )
        given(jobApplicationRepository.findByIdForUpdate(1L)).willReturn(application)
        given(formVersionRepository.findByFormIdAndVersion(10L, 1)).willReturn(formVersionOf())
        given(jobApplicationSubmissionRepository.findTopByApplicationIdOrderBySubmissionNumberDesc(1L))
            .willReturn(
                JobApplicationSubmission(
                    applicationId = 1L,
                    submissionNumber = 1,
                    formId = 10L,
                    formVersion = 1,
                    answers = "[]",
                    submittedAt = fixedTime,
                ),
            )

        service.executeAction(1L, 1L, JobApplicationActionRequest(JobApplicationAction.RESUBMIT))

        val submissionCaptor = ArgumentCaptor.forClass(JobApplicationSubmission::class.java)
        verify(jobApplicationSubmissionRepository).save(submissionCaptor.capture())
        assertThat(submissionCaptor.value.submissionNumber).isEqualTo(2)
    }

    @Test
    fun `필수 답변이 비어 있으면 SUBMIT하면 ApplicationRequiredAnswerMissingException을 던진다`() {
        val application = draftOf(formId = 10L, formVersion = 1, answers = "[]")
        given(jobApplicationRepository.findByIdForUpdate(1L)).willReturn(application)
        given(formVersionRepository.findByFormIdAndVersion(10L, 1)).willReturn(formVersionOf())

        assertThatThrownBy { service.executeAction(1L, 1L, JobApplicationActionRequest(JobApplicationAction.SUBMIT)) }
            .isInstanceOf(ApplicationRequiredAnswerMissingException::class.java)
    }

    @Test
    fun `필수 답변 value가 명시적 JSON null이면 SUBMIT하면 ApplicationRequiredAnswerMissingException을 던진다`() {
        // Jackson은 {"fieldId":"motivation","value":null}을 역직렬화할 때 Kotlin null이 아닌
        // NullNode Instance를 채운다(PR #129 Review 반영) — 이 차이를 검증한다.
        val answers =
            jsonMapper.writeValueAsString(
                listOf(ApplicationAnswer(fieldId = "motivation", value = jsonMapper.readTree("null"))),
            )
        val application = draftOf(formId = 10L, formVersion = 1, answers = answers)
        given(jobApplicationRepository.findByIdForUpdate(1L)).willReturn(application)
        given(formVersionRepository.findByFormIdAndVersion(10L, 1)).willReturn(formVersionOf())

        assertThatThrownBy { service.executeAction(1L, 1L, JobApplicationActionRequest(JobApplicationAction.SUBMIT)) }
            .isInstanceOf(ApplicationRequiredAnswerMissingException::class.java)
    }

    @Test
    fun `DRAFT가 아닌 지원서를 SUBMIT하면 ApplicationActionNotAvailableException을 던진다`() {
        given(jobApplicationRepository.findByIdForUpdate(1L))
            .willReturn(draftOf(status = JobApplicationStatus.SUBMITTED))

        assertThatThrownBy { service.executeAction(1L, 1L, JobApplicationActionRequest(JobApplicationAction.SUBMIT)) }
            .isInstanceOf(ApplicationActionNotAvailableException::class.java)
    }

    @Test
    fun `SUBMITTED 지원서를 REQUEST_EDIT하면 EDIT_REQUESTED로 전이한다`() {
        given(jobApplicationRepository.findByIdForUpdate(1L))
            .willReturn(draftOf(status = JobApplicationStatus.SUBMITTED))

        val result = service.executeAction(1L, 1L, JobApplicationActionRequest(JobApplicationAction.REQUEST_EDIT))

        assertThat(result.status).isEqualTo(JobApplicationStatus.EDIT_REQUESTED)
    }

    @Test
    fun `EDIT_ALLOWED 지원서를 RESUBMIT하면 SUBMITTED로 전이한다`() {
        val application =
            draftOf(
                status = JobApplicationStatus.EDIT_ALLOWED,
                formId = 10L,
                formVersion = 1,
                answers = answersJsonOf("motivation"),
            )
        given(jobApplicationRepository.findByIdForUpdate(1L)).willReturn(application)
        given(formVersionRepository.findByFormIdAndVersion(10L, 1)).willReturn(formVersionOf())

        val result = service.executeAction(1L, 1L, JobApplicationActionRequest(JobApplicationAction.RESUBMIT))

        assertThat(result.status).isEqualTo(JobApplicationStatus.SUBMITTED)
    }

    @Test
    fun `REVISION_REQUESTED 지원서를 RESUBMIT하면 SUBMITTED로 전이한다`() {
        val application =
            draftOf(
                status = JobApplicationStatus.REVISION_REQUESTED,
                formId = 10L,
                formVersion = 1,
                answers = answersJsonOf("motivation"),
            )
        given(jobApplicationRepository.findByIdForUpdate(1L)).willReturn(application)
        given(formVersionRepository.findByFormIdAndVersion(10L, 1)).willReturn(formVersionOf())

        val result = service.executeAction(1L, 1L, JobApplicationActionRequest(JobApplicationAction.RESUBMIT))

        assertThat(result.status).isEqualTo(JobApplicationStatus.SUBMITTED)
    }

    @Test
    fun `REVISION_REQUESTED 지원서를 RESUBMIT하면 기존 statusReason을 초기화한다`() {
        val application =
            draftOf(
                status = JobApplicationStatus.REVISION_REQUESTED,
                formId = 10L,
                formVersion = 1,
                answers = answersJsonOf("motivation"),
                statusReason = "자기소개서 보완 필요",
            )
        given(jobApplicationRepository.findByIdForUpdate(1L)).willReturn(application)
        given(formVersionRepository.findByFormIdAndVersion(10L, 1)).willReturn(formVersionOf())

        val result = service.executeAction(1L, 1L, JobApplicationActionRequest(JobApplicationAction.RESUBMIT))

        assertThat(result.statusReason).isNull()
    }

    @Test
    fun `DRAFT 지원서를 WITHDRAW하면 WITHDRAWN으로 전이하고 withdrawnAt을 기록한다`() {
        given(jobApplicationRepository.findByIdForUpdate(1L)).willReturn(draftOf())

        val result = service.executeAction(1L, 1L, JobApplicationActionRequest(JobApplicationAction.WITHDRAW))

        assertThat(result.status).isEqualTo(JobApplicationStatus.WITHDRAWN)
        assertThat(result.withdrawnAt).isNotNull()
    }

    @Test
    fun `WITHDRAW하면 상태 이력만 기록하고 제출 Snapshot은 남기지 않는다`() {
        given(jobApplicationRepository.findByIdForUpdate(1L)).willReturn(draftOf())

        service.executeAction(1L, 1L, JobApplicationActionRequest(JobApplicationAction.WITHDRAW))

        val historyCaptor = ArgumentCaptor.forClass(JobApplicationStatusHistory::class.java)
        verify(jobApplicationStatusHistoryRepository).save(historyCaptor.capture())
        assertThat(historyCaptor.value.fromStatus).isEqualTo(JobApplicationStatus.DRAFT)
        assertThat(historyCaptor.value.toStatus).isEqualTo(JobApplicationStatus.WITHDRAWN)
        assertThat(historyCaptor.value.action).isEqualTo("WITHDRAW")
        verify(jobApplicationSubmissionRepository, never()).save(any())
    }

    @Test
    fun `최종 상태의 지원서를 WITHDRAW하면 ApplicationActionNotAvailableException을 던진다`() {
        given(jobApplicationRepository.findByIdForUpdate(1L))
            .willReturn(draftOf(status = JobApplicationStatus.APPROVED))

        assertThatThrownBy { service.executeAction(1L, 1L, JobApplicationActionRequest(JobApplicationAction.WITHDRAW)) }
            .isInstanceOf(ApplicationActionNotAvailableException::class.java)
    }

    @Test
    fun `존재하지 않는 지원서에 Action을 수행하면 ApplicationNotFoundException을 던진다`() {
        given(jobApplicationRepository.findByIdForUpdate(999L)).willReturn(null)

        assertThatThrownBy {
            service.executeAction(999L, 1L, JobApplicationActionRequest(JobApplicationAction.WITHDRAW))
        }.isInstanceOf(ApplicationNotFoundException::class.java)
    }

    @Test
    fun `다른 학생의 지원서에 Action을 수행하면 ApplicationAccessForbiddenException을 던진다`() {
        given(jobApplicationRepository.findByIdForUpdate(1L)).willReturn(draftOf(applicantMemberId = 1L))

        assertThatThrownBy {
            service.executeAction(1L, 2L, JobApplicationActionRequest(JobApplicationAction.WITHDRAW))
        }.isInstanceOf(ApplicationAccessForbiddenException::class.java)
    }

    // ---------- 첨부파일 연동(Issue #134) ----------

    private fun fileSnapshotOf(
        fileId: Long,
        originalName: String = "file-$fileId.pdf",
    ) = FileSnapshot(fileId = fileId, originalName = originalName, contentType = "application/pdf", size = 100)

    // Kotlin에서 Mockito의 any()는 null을 반환해 Kotlin이 non-null로 추론하는 Object 타입 인자에
    // 쓰면 NullPointerException이 난다(이 파일의 anyJobApplication()과 같은 이유). ownerId 같은
    // Long Parameter는 실제로는 primitive long으로 컴파일되므로 anyLong()이면 충분하다.
    private fun anyFileOwnerType(): FileOwnerType = any(FileOwnerType::class.java) ?: FileOwnerType.JOB_APPLICATION

    private fun anyFilePurpose(): FilePurpose = any(FilePurpose::class.java) ?: FilePurpose.JOB_APPLICATION

    @Suppress("UNCHECKED_CAST")
    private fun anyFileIdCollection(): Collection<Long> =
        any(Collection::class.java) as Collection<Long>? ?: emptyList()

    // 유지·추가·제거 Diff 로직 자체는 syncApplicationFiles를 직접 호출하는 JobApplicationFileSyncTest가
    // 담당한다(detekt LargeClass 회피 목적도 있음, 순수 함수라 Service 전체를 세팅하지 않고도
    // 검증할 수 있다). 이 Class는 executeAction이 Action별로 그 함수를 실제로 호출/생략하는지(PR
    // #142 Review 반영 -- 기존에는 "생략" Case만 있고 "호출" Case가 없어 syncApplicationFiles 호출
    // 자체를 지워도 Test가 통과했다), 응답에 파일 목록을 올바르게 싣는지만 확인한다.

    @Test
    fun `SUBMIT하면 답변의 fileIds를 실제로 File 도메인에 연결한다`() {
        val answers =
            jsonMapper.writeValueAsString(
                listOf(ApplicationAnswer(fieldId = "resume", value = null, fileIds = listOf(1L, 2L))),
            )
        val application = draftOf(formId = 10L, formVersion = 1, answers = answers)
        given(jobApplicationRepository.findByIdForUpdate(1L)).willReturn(application)
        given(formVersionRepository.findByFormIdAndVersion(10L, 1))
            .willReturn(formVersionOf(requiredKeys = listOf("resume")))
        given(fileLinkPort.linkedFilesOf(FileOwnerType.JOB_APPLICATION, 1L)).willReturn(emptyList())

        service.executeAction(1L, 1L, JobApplicationActionRequest(JobApplicationAction.SUBMIT))

        verify(fileLinkPort).validateAndLink(
            requesterId = 1L,
            fileIds = listOf(1L, 2L),
            purpose = FilePurpose.JOB_APPLICATION,
            ownerId = 1L,
        )
    }

    @Test
    fun `WITHDRAW하면 첨부파일을 연결·해제하지 않는다`() {
        given(jobApplicationRepository.findByIdForUpdate(1L)).willReturn(draftOf())
        given(fileLinkPort.linkedFilesOf(FileOwnerType.JOB_APPLICATION, 1L)).willReturn(emptyList())

        service.executeAction(1L, 1L, JobApplicationActionRequest(JobApplicationAction.WITHDRAW))

        verify(fileLinkPort, never()).unlinkAllOf(anyFileOwnerType(), anyLong())
        verify(fileLinkPort, never()).validateAndLink(anyLong(), anyFileIdCollection(), anyFilePurpose(), anyLong())
    }

    // 새 초안 생성 응답이 첨부파일 목록이 비어 있고 File 도메인을 조회하지 않는지는 createDraft
    // 절의 `지원 가능하면 초안을 생성하고 ...` Test가 함께 검증한다(중복 Service 세팅 회피).

    @Test
    fun `임시저장 응답에는 현재 연결된 첨부파일 목록이 담긴다`() {
        given(jobApplicationRepository.findById(1L)).willReturn(Optional.of(draftOf()))
        given(fileLinkPort.linkedFilesOf(FileOwnerType.JOB_APPLICATION, 1L)).willReturn(listOf(fileSnapshotOf(5L)))

        val result = service.saveDraft(1L, 1L, SaveJobApplicationDraftRequest())

        assertThat(result.files).hasSize(1)
        assertThat(result.files[0].fileId).isEqualTo(5L)
        assertThat(result.files[0].downloadUrl).isEqualTo("/api/v1/files/5/download")
    }

    // ---------- getHistory ----------

    @Test
    fun `본인 지원서의 상태 이력을 오래된 순으로 반환한다`() {
        given(jobApplicationRepository.findById(1L)).willReturn(Optional.of(draftOf(applicantMemberId = 1L)))
        val history =
            JobApplicationStatusHistory(
                applicationId = 1L,
                fromStatus = JobApplicationStatus.DRAFT,
                toStatus = JobApplicationStatus.SUBMITTED,
                action = "SUBMIT",
                actorMemberId = 1L,
                reason = null,
            ).apply {
                id = 1L
                createdAt = fixedTime
            }
        given(jobApplicationStatusHistoryRepository.findByApplicationIdOrderByCreatedAtAsc(1L))
            .willReturn(listOf(history))

        val result = service.getHistory(1L, 1L)

        assertThat(result).hasSize(1)
        assertThat(result[0].fromStatus).isEqualTo(JobApplicationStatus.DRAFT)
        assertThat(result[0].toStatus).isEqualTo(JobApplicationStatus.SUBMITTED)
        assertThat(result[0].action).isEqualTo("SUBMIT")
    }

    @Test
    fun `다른 학생의 지원서 이력을 조회하면 ApplicationAccessForbiddenException을 던진다`() {
        given(jobApplicationRepository.findById(1L)).willReturn(Optional.of(draftOf(applicantMemberId = 1L)))

        assertThatThrownBy { service.getHistory(1L, 2L) }.isInstanceOf(ApplicationAccessForbiddenException::class.java)
    }

    @Test
    fun `존재하지 않는 지원서 이력을 조회하면 ApplicationNotFoundException을 던진다`() {
        given(jobApplicationRepository.findById(999L)).willReturn(Optional.empty())

        assertThatThrownBy { service.getHistory(999L, 1L) }.isInstanceOf(ApplicationNotFoundException::class.java)
    }

    // list(Issue #184)/getDetail(Issue #184) Test는 별도 Class(JobApplicationServiceImplQueryTest)로
    // 분리했다(detekt LargeClass 회피 목적, JobApplicationFileSyncTest 분리와 같은 이유).
}
