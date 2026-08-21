package team.inreok.getiserver.domain.application.service.impl

import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.application.dto.JobApplicationAdminActionRequest
import team.inreok.getiserver.domain.application.dto.JobApplicationAdminListItemResponse
import team.inreok.getiserver.domain.application.dto.JobApplicationAdminListResponse
import team.inreok.getiserver.domain.application.dto.JobApplicationDraftResponse
import team.inreok.getiserver.domain.application.dto.JobApplicationStatusHistoryResponse
import team.inreok.getiserver.domain.application.entity.JobApplication
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus
import team.inreok.getiserver.domain.application.event.JobApplicationReviewedEvent
import team.inreok.getiserver.domain.application.exception.ApplicationNotFoundException
import team.inreok.getiserver.domain.application.exception.ApplicationReviewForbiddenException
import team.inreok.getiserver.domain.application.repository.JobApplicationRepository
import team.inreok.getiserver.domain.application.repository.JobApplicationStatusHistoryRepository
import team.inreok.getiserver.domain.application.service.JobApplicationAdminService
import team.inreok.getiserver.domain.application.service.escapeLikePattern
import team.inreok.getiserver.domain.company.query.CompanyQuery
import team.inreok.getiserver.domain.company.query.CompanySummary
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.file.link.FileLinkPort
import team.inreok.getiserver.domain.job.query.JobApplicationAdminFilterQueryPort
import team.inreok.getiserver.domain.job.query.JobApplicationJobSnapshot
import team.inreok.getiserver.domain.job.query.JobApplicationSnapshotQueryPort
import team.inreok.getiserver.domain.member.entity.type.DepartmentType
import team.inreok.getiserver.domain.member.query.InquiryMemberSnapshot
import team.inreok.getiserver.domain.member.query.InquiryMemberSnapshotQueryPort
import tools.jackson.databind.ObjectMapper

@Service
class JobApplicationAdminServiceImpl(
    private val jobApplicationRepository: JobApplicationRepository,
    private val jobApplicationSnapshotQueryPort: JobApplicationSnapshotQueryPort,
    // 기업·담당 교사 Filter(Issue #181)가 companyId/managerMemberId/mineOnly 조건을 만족하는 Job
    // id 집합을 얻는 데 쓴다(Module 경계 유지, JobApplicationAdminFilterQueryPort KDoc 참고).
    private val jobApplicationAdminFilterQueryPort: JobApplicationAdminFilterQueryPort,
    private val jobApplicationStatusHistoryRepository: JobApplicationStatusHistoryRepository,
    private val fileLinkPort: FileLinkPort,
    private val companyQuery: CompanyQuery,
    // 담당 교사 이름 조회는 email/phone 등 민감한 Field 없이 이름만 노출하는 기존 계약을
    // 재사용한다(Issue #172, `InquiryMemberSnapshotQueryPort` KDoc의 "inquiry 담당자" 재사용 판단과
    // 같은 이유 -- 새로 Named Interface를 만들지 않는다).
    private val inquiryMemberSnapshotQueryPort: InquiryMemberSnapshotQueryPort,
    private val eventPublisher: ApplicationEventPublisher,
    private val objectMapper: ObjectMapper,
) : JobApplicationAdminService {
    @Transactional(readOnly = true)
    override fun list(
        jobId: Long?,
        status: JobApplicationStatus?,
        applicantName: String?,
        cohort: Int?,
        department: DepartmentType?,
        companyId: Long?,
        managerMemberId: Long?,
        mineOnly: Boolean,
        requesterMemberId: Long,
        pageable: Pageable,
    ): JobApplicationAdminListResponse {
        // 검색어를 보내지 않은 경우와 공백만 보낸 경우를 모두 "검색어 없음"으로 취급한다
        // (InquiryServiceImpl.listAdmin과 동일한 관례).
        val trimmedApplicantName = applicantName?.trim()?.takeIf { it.isNotEmpty() }
        val escapedApplicantName = trimmedApplicantName?.let(::escapeLikePattern)
        val mineOnlyMemberId = if (mineOnly) requesterMemberId else null

        // companyId/managerMemberId/mineOnly 중 하나라도 지정됐을 때만 job 도메인에 배치 조회를
        // 요청한다(불필요한 Query 방지, JobApplicationAdminFilterQueryPort KDoc 참고). 셋 다
        // null이면 jobIds Filter 자체를 적용하지 않는다.
        val hasJobFilter = companyId != null || managerMemberId != null || mineOnly
        val filterJobIds =
            if (hasJobFilter) {
                jobApplicationAdminFilterQueryPort.findIdsByFilters(companyId, managerMemberId, mineOnlyMemberId)
            } else {
                emptySet()
            }

        val page =
            jobApplicationRepository.search(
                jobId = jobId,
                status = status,
                hasApplicantName = trimmedApplicantName != null,
                applicantName = escapedApplicantName ?: "",
                cohort = cohort,
                department = department?.name,
                hasJobFilter = hasJobFilter,
                jobIds = filterJobIds,
                pageable = pageable,
            )

        // 항목마다 공고·기업·담당자를 개별 조회하면 Page 항목 수만큼 Query가 늘어난다(N+1).
        // 이번 Page에 등장하는 공고/기업/담당자 id를 모아 한 번에 배치 조회한다
        // (InquiryServiceImpl.searchForAdmin의 memberIds 배치 조회와 동일한 원칙).
        val jobIds = page.content.map { it.jobId }.toSet()
        val jobs = if (jobIds.isEmpty()) emptyMap() else jobApplicationSnapshotQueryPort.findAllByIds(jobIds)
        val companyIds = jobs.values.map { it.companyId }.toSet()
        val companies = if (companyIds.isEmpty()) emptyMap() else companyQuery.findActiveSummaries(companyIds)
        val managerIds = jobs.values.mapNotNull { it.managerMemberId }.toSet()
        val managers = if (managerIds.isEmpty()) emptyMap() else inquiryMemberSnapshotQueryPort.findAllByIds(managerIds)

        return JobApplicationAdminListResponse(
            content = page.content.map { toListItem(it, jobs, companies, managers) },
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            first = page.isFirst,
            last = page.isLast,
        )
    }

    @Transactional(readOnly = true)
    override fun getDetail(applicationId: Long): JobApplicationDraftResponse {
        val application = findApplication(applicationId)
        val job = jobApplicationSnapshotQueryPort.findById(application.jobId)
        return toJobApplicationDraftResponse(
            objectMapper,
            application,
            currentFiles(application),
            jobTitle = job?.title,
            companyName = job?.let { companyNameOf(it.companyId) },
            managerMemberId = job?.managerMemberId,
            managerName = job?.managerMemberId?.let { managerNameOf(it) },
        )
    }

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
        // 응답 조립(공고명·기업명·담당자, Issue #172)에도 그대로 재사용하도록 권한 판정 이전에
        // 한 번만 조회한다(개발자 여부와 무관하게 필요, 기존에는 개발자면 조회 자체를 생략했다).
        val job = jobApplicationSnapshotQueryPort.findById(application.jobId)
        requireManagerOrDeveloper(job, requesterMemberId, isDeveloper)

        val fromStatus = application.status
        requireAllowedAdminTransition(fromStatus, request.action)
        applyJobApplicationAdminAction(application, request.action, request.reason)

        jobApplicationRepository.flush()
        // 상태 변경(flush)과 이력 저장이 같은 Transaction(@Transactional) 안에서 원자적으로
        // 처리된다(요구사항 "Transaction 일관성" 절, Issue #133).
        recordStatusHistory(
            jobApplicationStatusHistoryRepository,
            application,
            fromStatus,
            request.action.name,
            requesterMemberId,
            request.reason,
        )
        // 지원자에게 검토 결과를 알린다(Issue #135). 상태 전이·이력 기록과 같은 Transaction 안에서
        // 발행해, 검토 Action이 Rollback되면 알림도 만들어지지 않도록 한다(실제 알림 생성은
        // domain.notification의 AFTER_COMMIT Listener가 담당, JobApplicationReviewedEvent KDoc 참고).
        eventPublisher.publishEvent(
            JobApplicationReviewedEvent(
                applicationId = requireNotNull(application.id),
                studentMemberId = application.applicantMemberId,
                action = request.action.name,
                reason = request.reason,
            ),
        )
        return toJobApplicationDraftResponse(
            objectMapper,
            application,
            currentFiles(application),
            jobTitle = job?.title,
            companyName = job?.let { companyNameOf(it.companyId) },
            managerMemberId = job?.managerMemberId,
            managerName = job?.managerMemberId?.let { managerNameOf(it) },
        )
    }

    @Transactional(readOnly = true)
    override fun getHistory(applicationId: Long): List<JobApplicationStatusHistoryResponse> {
        val application = findApplication(applicationId)
        return jobApplicationStatusHistoryRepository
            .findByApplicationIdOrderByCreatedAtAsc(requireNotNull(application.id))
            .map(::toJobApplicationStatusHistoryResponse)
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
        job: JobApplicationJobSnapshot?,
        requesterMemberId: Long,
        isDeveloper: Boolean,
    ) {
        if (isDeveloper) return
        val isManager =
            job != null && (requesterMemberId == job.createdByMemberId || requesterMemberId == job.managerMemberId)
        if (!isManager) throw ApplicationReviewForbiddenException()
    }

    // getDetail/executeAction 응답에 실을 "현재 연결된 첨부파일" 목록을 조회한다(Issue #134,
    // toJobApplicationDraftResponse KDoc 참고).
    private fun currentFiles(application: JobApplication) =
        fileLinkPort.linkedFilesOf(FileOwnerType.JOB_APPLICATION, requireNotNull(application.id))

    // getDetail/executeAction(단건)에서 쓰는 기업명 단건 조회다. list()는 N+1을 피하기 위해
    // companyQuery.findActiveSummaries로 별도 배치 조회한다.
    private fun companyNameOf(companyId: Long): String? = companyQuery.findActiveSummary(companyId)?.name

    // getDetail/executeAction(단건)에서 쓰는 담당자 이름 단건 조회다. InquiryMemberSnapshotQueryPort는
    // 배치 Method만 공개하므로(요구사항 목록 응답 배치 조회 관례) 단건 호출도 Set 하나로 부른다.
    private fun managerNameOf(managerMemberId: Long): String? =
        inquiryMemberSnapshotQueryPort.findAllByIds(setOf(managerMemberId))[managerMemberId]?.name

    private fun toListItem(
        application: JobApplication,
        jobs: Map<Long, JobApplicationJobSnapshot>,
        companies: Map<Long, CompanySummary>,
        managers: Map<Long, InquiryMemberSnapshot>,
    ): JobApplicationAdminListItemResponse {
        val job = jobs[application.jobId]
        return JobApplicationAdminListItemResponse(
            applicationId = requireNotNull(application.id),
            jobId = application.jobId,
            applicantMemberId = application.applicantMemberId,
            applicantName = application.applicantName,
            applicantCohort = application.applicantCohort,
            applicantDepartment = application.applicantDepartment,
            jobTitle = job?.title,
            companyName = job?.let { companies[it.companyId]?.name },
            managerMemberId = job?.managerMemberId,
            managerName = job?.managerMemberId?.let { managers[it]?.name },
            status = application.status,
            submittedAt = application.submittedAt,
            createdAt = requireNotNull(application.createdAt),
        )
    }
}
