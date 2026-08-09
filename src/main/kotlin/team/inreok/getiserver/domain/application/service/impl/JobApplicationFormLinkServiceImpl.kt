package team.inreok.getiserver.domain.application.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.application.dto.JobApplicationFormLinkRequest
import team.inreok.getiserver.domain.application.dto.JobApplicationFormLinkResponse
import team.inreok.getiserver.domain.application.entity.Form
import team.inreok.getiserver.domain.application.entity.JobApplicationForm
import team.inreok.getiserver.domain.application.entity.type.FormStatus
import team.inreok.getiserver.domain.application.entity.type.FormType
import team.inreok.getiserver.domain.application.exception.FormNotActiveException
import team.inreok.getiserver.domain.application.exception.FormNotFoundException
import team.inreok.getiserver.domain.application.exception.FormNotOwnedException
import team.inreok.getiserver.domain.application.exception.InvalidFormFieldException
import team.inreok.getiserver.domain.application.exception.JobApplicationMethodNotInternalException
import team.inreok.getiserver.domain.application.exception.JobManageForbiddenException
import team.inreok.getiserver.domain.application.exception.JobNotFoundException
import team.inreok.getiserver.domain.application.repository.FormRepository
import team.inreok.getiserver.domain.application.repository.JobApplicationFormRepository
import team.inreok.getiserver.domain.application.service.JobApplicationFormLinkService
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.query.JobApplicationJobSnapshot
import team.inreok.getiserver.domain.job.query.JobApplicationSnapshotQueryPort

@Service
class JobApplicationFormLinkServiceImpl(
    private val jobApplicationSnapshotQueryPort: JobApplicationSnapshotQueryPort,
    private val formRepository: FormRepository,
    private val jobApplicationFormRepository: JobApplicationFormRepository,
) : JobApplicationFormLinkService {
    @Transactional
    override fun link(
        jobId: Long,
        requesterMemberId: Long,
        isDeveloper: Boolean,
        request: JobApplicationFormLinkRequest,
    ): JobApplicationFormLinkResponse {
        val job = jobApplicationSnapshotQueryPort.findById(jobId) ?: throw JobNotFoundException(jobId)
        validateInternalApplicationMethod(job)
        validateJobManager(job, requesterMemberId, isDeveloper)

        val form = formRepository.findById(request.formId).orElseThrow { FormNotFoundException(request.formId) }
        validateLinkableForm(form, requesterMemberId)

        val saved = jobApplicationFormRepository.saveAndFlush(upsertLink(jobId, request.formId, requesterMemberId))

        return JobApplicationFormLinkResponse(
            jobId = jobId,
            formId = requireNotNull(form.id),
            formVersion = form.currentVersion,
            updatedAt = requireNotNull(saved.updatedAt),
        )
    }

    private fun upsertLink(
        jobId: Long,
        formId: Long,
        requesterMemberId: Long,
    ): JobApplicationForm =
        jobApplicationFormRepository.findById(jobId).orElse(null)?.apply {
            this.formId = formId
            linkedByMemberId = requesterMemberId
        } ?: JobApplicationForm(jobId = jobId, formId = formId, linkedByMemberId = requesterMemberId)

    private fun validateInternalApplicationMethod(job: JobApplicationJobSnapshot) {
        if (job.applicationMethod != ApplicationMethod.INTERNAL.name) throw JobApplicationMethodNotInternalException()
    }

    // 개발자는 담당자 검증만 우회한다 — 양식 소유권 검증([validateLinkableForm])은 역할과 무관하게
    // 항상 적용한다(요구사항 3절 "개발자 역할만으로 다른 사용자의 개인 양식을 ... 공고 적용할 수
    // 있게 만들면 안 된다").
    private fun validateJobManager(
        job: JobApplicationJobSnapshot,
        requesterMemberId: Long,
        isDeveloper: Boolean,
    ) {
        val isManager = requesterMemberId == job.createdByMemberId || requesterMemberId == job.managerMemberId
        if (!isDeveloper && !isManager) throw JobManageForbiddenException()
    }

    private fun validateLinkableForm(
        form: Form,
        requesterMemberId: Long,
    ) {
        if (form.ownerMemberId != requesterMemberId) throw FormNotOwnedException()
        validateFormApplicable(form)
    }

    private fun validateFormApplicable(form: Form) {
        if (form.formType != FormType.JOB) throw InvalidFormFieldException("JOB 유형 양식만 공고에 연결할 수 있습니다.")
        if (form.status != FormStatus.ACTIVE) throw FormNotActiveException()
    }
}
