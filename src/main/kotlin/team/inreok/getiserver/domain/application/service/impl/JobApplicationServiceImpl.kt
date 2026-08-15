package team.inreok.getiserver.domain.application.service.impl

import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.application.dto.ApplicationAnswer
import team.inreok.getiserver.domain.application.dto.CreateJobApplicationRequest
import team.inreok.getiserver.domain.application.dto.JobApplicationAction
import team.inreok.getiserver.domain.application.dto.JobApplicationActionRequest
import team.inreok.getiserver.domain.application.dto.JobApplicationDraftResponse
import team.inreok.getiserver.domain.application.dto.JobApplicationStatusHistoryResponse
import team.inreok.getiserver.domain.application.dto.JobEligibilityResponse
import team.inreok.getiserver.domain.application.dto.SaveJobApplicationDraftRequest
import team.inreok.getiserver.domain.application.entity.JobApplication
import team.inreok.getiserver.domain.application.entity.type.JobApplicationEligibilityReason
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus
import team.inreok.getiserver.domain.application.exception.ActiveApplicationExistsException
import team.inreok.getiserver.domain.application.exception.ApplicationAccessForbiddenException
import team.inreok.getiserver.domain.application.exception.ApplicationActionNotAvailableException
import team.inreok.getiserver.domain.application.exception.ApplicationNotFoundException
import team.inreok.getiserver.domain.application.exception.JobNotApplicableException
import team.inreok.getiserver.domain.application.repository.FormRepository
import team.inreok.getiserver.domain.application.repository.FormVersionRepository
import team.inreok.getiserver.domain.application.repository.JobApplicationFormRepository
import team.inreok.getiserver.domain.application.repository.JobApplicationRepository
import team.inreok.getiserver.domain.application.repository.JobApplicationStatusHistoryRepository
import team.inreok.getiserver.domain.application.repository.JobApplicationSubmissionRepository
import team.inreok.getiserver.domain.application.service.JobApplicationService
import team.inreok.getiserver.domain.job.query.JobApplicationSnapshotQueryPort
import team.inreok.getiserver.domain.member.query.MemberApplicantSnapshotQueryPort
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime

// 학생 Action별 시작 가능 상태 집합이다(요구사항 "상태 전이 / Flow" 절). RESUBMIT은
// EDIT_ALLOWED/REVISION_REQUESTED 모두에서 SUBMITTED로 전이한다. EDIT_REQUESTED/EDIT_ALLOWED가
// 만드는 상태(EDIT_ALLOWED 등)는 교사 Action(Phase 4) 완료 전까지는 실제로 도달할 수 없지만,
// Service 레벨 상태 검증은 먼저 구현해 둔다.
private val JOB_APPLICATION_ACTION_ALLOWED_FROM: Map<JobApplicationAction, Set<JobApplicationStatus>> =
    mapOf(
        JobApplicationAction.SUBMIT to setOf(JobApplicationStatus.DRAFT),
        JobApplicationAction.REQUEST_EDIT to setOf(JobApplicationStatus.SUBMITTED),
        JobApplicationAction.RESUBMIT to
            setOf(JobApplicationStatus.EDIT_ALLOWED, JobApplicationStatus.REVISION_REQUESTED),
        JobApplicationAction.WITHDRAW to
            setOf(
                JobApplicationStatus.DRAFT,
                JobApplicationStatus.SUBMITTED,
                JobApplicationStatus.EDIT_REQUESTED,
                JobApplicationStatus.EDIT_ALLOWED,
                JobApplicationStatus.REVISION_REQUESTED,
            ),
    )

// saveDraft(임시저장)를 허용하는 상태다. Phase 2는 DRAFT만 허용했고, Phase 3는 교사가 재작성을
// 허용/요청한 상태(EDIT_ALLOWED/REVISION_REQUESTED)에서도 재작성 후 RESUBMIT할 수 있어야 한다.
private val SAVE_DRAFT_ALLOWED_STATUSES: Set<JobApplicationStatus> =
    setOf(JobApplicationStatus.DRAFT, JobApplicationStatus.EDIT_ALLOWED, JobApplicationStatus.REVISION_REQUESTED)

// executeAction()의 상태 전이 검증과 실제 전이를 순수 함수로 분리했다. JobApplicationServiceImpl의
// Method 개수를 detekt TooManyFunctions 한도 안에서 유지하기 위함이다(computeEligibilityReason과
// 같은 이유). JOB_APPLICATION_ACTION_ALLOWED_FROM이 이 파일 Top-level private val이라 같은 파일의
// Top-level 함수로 둬야 접근할 수 있다.
private fun requireAllowedTransition(
    status: JobApplicationStatus,
    action: JobApplicationAction,
) {
    val allowedFrom = JOB_APPLICATION_ACTION_ALLOWED_FROM.getValue(action)
    if (status !in allowedFrom) {
        throw ApplicationActionNotAvailableException("현재 상태($status)에서는 $action Action을 수행할 수 없습니다.")
    }
}

private fun applyJobApplicationAction(
    formVersionRepository: FormVersionRepository,
    objectMapper: ObjectMapper,
    application: JobApplication,
    action: JobApplicationAction,
) {
    when (action) {
        JobApplicationAction.SUBMIT, JobApplicationAction.RESUBMIT -> {
            validateRequiredAnswersFilled(formVersionRepository, objectMapper, application)
            application.submittedAt = LocalDateTime.now()
            application.status = JobApplicationStatus.SUBMITTED
            // RESUBMIT 직전 REVISION_REQUESTED/EDIT_REQUESTED 단계에서 교사가 남긴 statusReason이
            // 재제출 후에도 남아있으면 이미 반영된 과거 사유가 그대로 노출된다(PR #130 Review 반영).
            application.statusReason = null
        }

        JobApplicationAction.REQUEST_EDIT -> {
            application.status = JobApplicationStatus.EDIT_REQUESTED
        }

        JobApplicationAction.WITHDRAW -> {
            application.withdrawnAt = LocalDateTime.now()
            application.status = JobApplicationStatus.WITHDRAWN
        }
    }
}

@Service
class JobApplicationServiceImpl(
    private val jobApplicationRepository: JobApplicationRepository,
    private val jobApplicationFormRepository: JobApplicationFormRepository,
    private val formRepository: FormRepository,
    private val formVersionRepository: FormVersionRepository,
    private val jobApplicationSnapshotQueryPort: JobApplicationSnapshotQueryPort,
    private val memberApplicantSnapshotQueryPort: MemberApplicantSnapshotQueryPort,
    private val jobApplicationStatusHistoryRepository: JobApplicationStatusHistoryRepository,
    private val jobApplicationSubmissionRepository: JobApplicationSubmissionRepository,
    private val objectMapper: ObjectMapper,
) : JobApplicationService {
    private val log = LoggerFactory.getLogger(JobApplicationServiceImpl::class.java)

    @Transactional(readOnly = true)
    override fun checkEligibility(
        jobId: Long,
        studentMemberId: Long,
    ): JobEligibilityResponse {
        val job = jobApplicationSnapshotQueryPort.findById(jobId)
        val member = memberApplicantSnapshotQueryPort.findById(studentMemberId)
        val reason =
            computeEligibilityReason(
                job = job,
                member = member,
                hasActiveLinkedForm = activeLinkedForm(jobApplicationFormRepository, formRepository, jobId) != null,
                hasActiveApplication = hasActiveApplication(jobId, studentMemberId),
                now = LocalDateTime.now(),
            )

        return JobEligibilityResponse(
            canApply = reason == JobApplicationEligibilityReason.AVAILABLE,
            eligibilityReason = reason,
            eligibilityMessage = eligibilityMessageOf(reason),
            availableActions =
                if (reason ==
                    JobApplicationEligibilityReason.AVAILABLE
                ) {
                    listOf("CREATE_DRAFT")
                } else {
                    emptyList()
                },
        )
    }

    @Transactional
    override fun createDraft(
        jobId: Long,
        studentMemberId: Long,
        request: CreateJobApplicationRequest,
    ): JobApplicationDraftResponse {
        val job = jobApplicationSnapshotQueryPort.findById(jobId)
        val member = memberApplicantSnapshotQueryPort.findById(studentMemberId)
        val linkedForm = activeLinkedForm(jobApplicationFormRepository, formRepository, jobId)
        val reason =
            computeEligibilityReason(
                job = job,
                member = member,
                hasActiveLinkedForm = linkedForm != null,
                hasActiveApplication = hasActiveApplication(jobId, studentMemberId),
                now = LocalDateTime.now(),
            )
        // AVAILABLE이 아닌 사유 중 ALREADY_APPLIED만 별도 Error Code로 구분한다(요구사항 8절이
        // JOB_NOT_APPLICABLE과 ACTIVE_APPLICATION_EXISTS를 별개 오류로 나열함).
        when (reason) {
            JobApplicationEligibilityReason.AVAILABLE -> Unit
            JobApplicationEligibilityReason.ALREADY_APPLIED -> throw ActiveApplicationExistsException()
            else -> throw JobNotApplicableException()
        }
        // reason == AVAILABLE이려면 checkEligibility의 NOT_ENROLLED 분기를 통과해야 하므로
        // member는 항상 존재한다.
        val safeMember = requireNotNull(member) { "AVAILABLE 판정은 member가 존재할 때만 나온다." }

        val nextAttempt =
            (
                jobApplicationRepository
                    .findTopByJobIdAndApplicantMemberIdOrderByAttemptNumberDesc(jobId, studentMemberId)
                    ?.attemptNumber ?: 0
            ) + 1

        val application =
            JobApplication(
                jobId = jobId,
                applicantMemberId = studentMemberId,
                attemptNumber = nextAttempt,
                contactEmail = safeMember.email,
                answers = writeAnswers(emptyList()),
            ).apply {
                formId = linkedForm?.id
                formVersion = linkedForm?.currentVersion
                if (request.prefillProfileFields) {
                    contactPhone = safeMember.phone
                    applicantName = safeMember.name
                    applicantCohort = safeMember.cohort
                    applicantDepartment = safeMember.department
                    applicantMajors = writeStringList(safeMember.majors)
                    applicantDesiredJob = safeMember.desiredJob
                    applicantTechStacks = writeStringList(safeMember.techStacks)
                }
            }

        return toJobApplicationDraftResponse(objectMapper, saveNewApplication(application))
    }

    @Transactional
    override fun saveDraft(
        applicationId: Long,
        studentMemberId: Long,
        request: SaveJobApplicationDraftRequest,
    ): JobApplicationDraftResponse {
        val application =
            jobApplicationRepository.findById(applicationId).orElseThrow { ApplicationNotFoundException(applicationId) }
        if (application.applicantMemberId != studentMemberId) throw ApplicationAccessForbiddenException()
        // Phase 3부터 EDIT_ALLOWED/REVISION_REQUESTED 상태에서도 재작성 후 RESUBMIT할 수 있다.
        if (application.status !in SAVE_DRAFT_ALLOWED_STATUSES) {
            throw ApplicationActionNotAvailableException("현재 상태(${application.status})의 지원서는 임시저장할 수 없습니다.")
        }

        request.contactPhone?.let { application.contactPhone = it }
        request.privacyConsent?.let { application.privacyConsent = it }
        request.answers?.let { application.answers = writeAnswers(it) }

        jobApplicationRepository.flush()
        return toJobApplicationDraftResponse(objectMapper, application)
    }

    @Transactional
    override fun executeAction(
        applicationId: Long,
        studentMemberId: Long,
        request: JobApplicationActionRequest,
    ): JobApplicationDraftResponse {
        // 같은 지원서에 대한 동시 Action(학생 WITHDRAW와 교사 APPROVE 등)이 상태를 모순되게
        // 만들지 않도록 Pessimistic Write Lock으로 조회한 뒤 재확인한다(요구사항 "동시성/데이터
        // 무결성" 절, ProgramRepository.findByIdForUpdate와 동일한 관례). 존재 여부를 알 수 없도록
        // 404가 아닌 403을 사용한다(요구사항 "권한" 절, saveDraft와 동일한 관례).
        val application =
            jobApplicationRepository.findByIdForUpdate(applicationId)
                ?: throw ApplicationNotFoundException(applicationId)
        if (application.applicantMemberId != studentMemberId) throw ApplicationAccessForbiddenException()

        val fromStatus = application.status
        requireAllowedTransition(fromStatus, request.action)
        applyJobApplicationAction(formVersionRepository, objectMapper, application, request.action)

        jobApplicationRepository.flush()
        // 상태 변경(flush)과 이력·Snapshot 저장이 같은 Transaction(@Transactional) 안에서
        // 원자적으로 처리된다(요구사항 "Transaction 일관성" 절, Issue #133).
        recordStatusHistory(
            jobApplicationStatusHistoryRepository,
            application,
            fromStatus,
            request.action.name,
            studentMemberId,
            reason = null,
        )
        if (request.action == JobApplicationAction.SUBMIT || request.action == JobApplicationAction.RESUBMIT) {
            recordSubmissionSnapshot(jobApplicationSubmissionRepository, application)
        }
        return toJobApplicationDraftResponse(objectMapper, application)
    }

    @Transactional(readOnly = true)
    override fun getHistory(
        applicationId: Long,
        studentMemberId: Long,
    ): List<JobApplicationStatusHistoryResponse> {
        val application =
            jobApplicationRepository.findById(applicationId).orElseThrow { ApplicationNotFoundException(applicationId) }
        if (application.applicantMemberId != studentMemberId) throw ApplicationAccessForbiddenException()
        return jobApplicationStatusHistoryRepository
            .findByApplicationIdOrderByCreatedAtAsc(applicationId)
            .map(::toJobApplicationStatusHistoryResponse)
    }

    // 호출부(createDraft)의 hasActiveApplication() 확인과 이 saveAndFlush 사이에는 DB 잠금이
    // 없어(TOCTOU), 같은 학생이 같은 공고에 거의 동시에 두 번 요청하면(중복 클릭 등) 두 요청 모두
    // 확인을 통과할 수 있다. uk_job_applications_active_singleton(활성 Row 최대 1건 Partial
    // Unique Index, V13 Migration)이 최종 방어선이다 — 위반하면 DB가
    // DataIntegrityViolationException을 던지고 여기서 ACTIVE_APPLICATION_EXISTS(409)로
    // 변환한다(PR #79 Review 반영, SearchReindexServiceImpl.triggerReindex()와 동일한 패턴).
    private fun saveNewApplication(application: JobApplication): JobApplication =
        try {
            jobApplicationRepository.saveAndFlush(application)
        } catch (ex: DataIntegrityViolationException) {
            // BusinessException은 cause를 받지 않아 원본 예외를 여기서 남긴다(detekt
            // SwallowedException 반영) — 실제 제약 위반 여부를 나중에 DB 로그로도 추적할 수 있다.
            log.warn("지원서 초안 동시 생성 요청이 DB 제약으로 차단됨(uk_job_applications_active_singleton)", ex)
            throw ActiveApplicationExistsException()
        }

    private fun hasActiveApplication(
        jobId: Long,
        studentMemberId: Long,
    ): Boolean =
        jobApplicationRepository
            .findByJobIdAndApplicantMemberIdAndStatusIn(
                jobId,
                studentMemberId,
                ACTIVE_JOB_APPLICATION_STATUSES,
            ).isNotEmpty()

    private fun writeStringList(values: List<String>): String? =
        if (values.isEmpty()) null else objectMapper.writeValueAsString(values)

    private fun writeAnswers(answers: List<ApplicationAnswer>): String = objectMapper.writeValueAsString(answers)
}
