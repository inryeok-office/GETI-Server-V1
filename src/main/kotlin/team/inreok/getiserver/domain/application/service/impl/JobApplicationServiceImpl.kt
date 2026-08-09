package team.inreok.getiserver.domain.application.service.impl

import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.application.dto.ApplicationAnswer
import team.inreok.getiserver.domain.application.dto.CreateJobApplicationRequest
import team.inreok.getiserver.domain.application.dto.JobApplicationDraftResponse
import team.inreok.getiserver.domain.application.dto.JobEligibilityResponse
import team.inreok.getiserver.domain.application.dto.SaveJobApplicationDraftRequest
import team.inreok.getiserver.domain.application.entity.Form
import team.inreok.getiserver.domain.application.entity.JobApplication
import team.inreok.getiserver.domain.application.entity.type.FormStatus
import team.inreok.getiserver.domain.application.entity.type.JobApplicationEligibilityReason
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
import team.inreok.getiserver.domain.job.query.JobApplicationSnapshotQueryPort
import team.inreok.getiserver.domain.member.query.MemberApplicantSnapshotQueryPort
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime

@Service
class JobApplicationServiceImpl(
    private val jobApplicationRepository: JobApplicationRepository,
    private val jobApplicationFormRepository: JobApplicationFormRepository,
    private val formRepository: FormRepository,
    private val jobApplicationSnapshotQueryPort: JobApplicationSnapshotQueryPort,
    private val memberApplicantSnapshotQueryPort: MemberApplicantSnapshotQueryPort,
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
                hasActiveLinkedForm = activeLinkedForm(jobId) != null,
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
        val linkedForm = activeLinkedForm(jobId)
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

        return toDraftResponse(saveNewApplication(application))
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
        // Phase 2는 DRAFT 임시저장만 다룬다. EDIT_ALLOWED/REVISION_REQUESTED 재제출 흐름은 Phase 3.
        if (application.status != JobApplicationStatus.DRAFT) {
            throw ApplicationActionNotAvailableException("DRAFT 상태의 지원서만 임시저장할 수 있습니다.")
        }

        request.contactPhone?.let { application.contactPhone = it }
        request.privacyConsent?.let { application.privacyConsent = it }
        request.answers?.let { application.answers = writeAnswers(it) }

        jobApplicationRepository.flush()
        return toDraftResponse(application)
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

    private fun activeLinkedForm(jobId: Long): Form? =
        jobApplicationFormRepository
            .findById(jobId)
            .orElse(null)
            ?.let { link -> formRepository.findById(link.formId).orElse(null) }
            ?.takeIf { it.status == FormStatus.ACTIVE }

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

    private fun toDraftResponse(application: JobApplication): JobApplicationDraftResponse =
        JobApplicationDraftResponse(
            applicationId = requireNotNull(application.id),
            jobId = application.jobId,
            formId = application.formId,
            formVersion = application.formVersion,
            status = application.status,
            statusReason = application.statusReason,
            contactEmail = application.contactEmail,
            contactPhone = application.contactPhone,
            privacyConsent = application.privacyConsent,
            applicantName = application.applicantName,
            applicantCohort = application.applicantCohort,
            applicantDepartment = application.applicantDepartment,
            applicantMajors = readStringList(application.applicantMajors),
            applicantDesiredJob = application.applicantDesiredJob,
            applicantTechStacks = readStringList(application.applicantTechStacks),
            answers = readAnswers(application.answers),
            submittedAt = application.submittedAt,
            createdAt = requireNotNull(application.createdAt),
            updatedAt = requireNotNull(application.updatedAt),
        )

    private fun writeStringList(values: List<String>): String? =
        if (values.isEmpty()) null else objectMapper.writeValueAsString(values)

    private fun readStringList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return objectMapper.readValue(json, Array<String>::class.java).toList()
    }

    private fun writeAnswers(answers: List<ApplicationAnswer>): String = objectMapper.writeValueAsString(answers)

    private fun readAnswers(json: String): List<ApplicationAnswer> {
        if (json.isBlank()) return emptyList()
        return objectMapper.readValue(json, Array<ApplicationAnswer>::class.java).toList()
    }
}
