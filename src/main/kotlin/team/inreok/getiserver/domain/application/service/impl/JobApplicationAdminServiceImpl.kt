package team.inreok.getiserver.domain.application.service.impl

import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.application.dto.JobApplicationAdminActionRequest
import team.inreok.getiserver.domain.application.dto.JobApplicationAdminListItemResponse
import team.inreok.getiserver.domain.application.dto.JobApplicationAdminListResponse
import team.inreok.getiserver.domain.application.dto.JobApplicationDraftResponse
import team.inreok.getiserver.domain.application.entity.JobApplication
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus
import team.inreok.getiserver.domain.application.exception.ApplicationNotFoundException
import team.inreok.getiserver.domain.application.exception.ApplicationReviewForbiddenException
import team.inreok.getiserver.domain.application.repository.JobApplicationRepository
import team.inreok.getiserver.domain.application.service.JobApplicationAdminService
import team.inreok.getiserver.domain.job.query.JobApplicationSnapshotQueryPort
import tools.jackson.databind.ObjectMapper

@Service
class JobApplicationAdminServiceImpl(
    private val jobApplicationRepository: JobApplicationRepository,
    private val jobApplicationSnapshotQueryPort: JobApplicationSnapshotQueryPort,
    private val objectMapper: ObjectMapper,
) : JobApplicationAdminService {
    @Transactional(readOnly = true)
    override fun list(
        jobId: Long?,
        status: JobApplicationStatus?,
        pageable: Pageable,
    ): JobApplicationAdminListResponse {
        val page = jobApplicationRepository.search(jobId, status, pageable)
        return JobApplicationAdminListResponse(
            content = page.content.map(::toListItem),
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            first = page.isFirst,
            last = page.isLast,
        )
    }

    @Transactional(readOnly = true)
    override fun getDetail(applicationId: Long): JobApplicationDraftResponse =
        toJobApplicationDraftResponse(objectMapper, findApplication(applicationId))

    @Transactional
    override fun executeAction(
        applicationId: Long,
        requesterMemberId: Long,
        isDeveloper: Boolean,
        request: JobApplicationAdminActionRequest,
    ): JobApplicationDraftResponse {
        // 여러 교사의 동시 검토(예: 한 명은 APPROVE, 다른 한 명은 REJECT)가 상태를 모순되게
        // 만들지 않도록 Pessimistic Write Lock으로 조회한 뒤 재확인한다(요구사항 "동시성/데이터
        // 무결성" 절, JobApplicationServiceImpl.executeAction과 동일한 관례).
        val application =
            jobApplicationRepository.findByIdForUpdate(applicationId)
                ?: throw ApplicationNotFoundException(applicationId)
        requireManagerOrDeveloper(application, requesterMemberId, isDeveloper)

        requireAllowedAdminTransition(application.status, request.action)
        applyJobApplicationAdminAction(application, request.action, request.reason)

        jobApplicationRepository.flush()
        return toJobApplicationDraftResponse(objectMapper, application)
    }

    // Issue #125는 "제출된 지원서"만 교사·개발자 조회 대상으로 한다. status 조건이 없는 단건 조회는
    // DRAFT(임시저장 중, 미제출)까지 그대로 노출하므로 여기서 명시적으로 걸러낸다(PR #130 Review
    // 반영). 존재 여부를 노출하지 않도록 list()의 search()와 동일하게 404로 처리한다.
    private fun findApplication(applicationId: Long): JobApplication {
        val application =
            jobApplicationRepository.findById(applicationId).orElseThrow { ApplicationNotFoundException(applicationId) }
        if (application.status == JobApplicationStatus.DRAFT) throw ApplicationNotFoundException(applicationId)
        return application
    }

    // 조회 권한(모든 교사)과 달리 상태 변경은 해당 공고의 등록자·담당 교사, 또는 개발자만 허용한다
    // (요구사항 "권한" 절). 공고가 이미 삭제되어 Snapshot을 찾을 수 없으면 개발자가 아닌 한 담당자
    // 여부를 확인할 수 없으므로 거부한다.
    private fun requireManagerOrDeveloper(
        application: JobApplication,
        requesterMemberId: Long,
        isDeveloper: Boolean,
    ) {
        if (isDeveloper) return
        val job = jobApplicationSnapshotQueryPort.findById(application.jobId)
        val isManager =
            job != null && (requesterMemberId == job.createdByMemberId || requesterMemberId == job.managerMemberId)
        if (!isManager) throw ApplicationReviewForbiddenException()
    }

    private fun toListItem(application: JobApplication): JobApplicationAdminListItemResponse =
        JobApplicationAdminListItemResponse(
            applicationId = requireNotNull(application.id),
            jobId = application.jobId,
            applicantMemberId = application.applicantMemberId,
            applicantName = application.applicantName,
            status = application.status,
            submittedAt = application.submittedAt,
            createdAt = requireNotNull(application.createdAt),
        )
}
