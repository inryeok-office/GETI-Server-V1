package team.inreok.getiserver.domain.job.service.impl

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.company.query.CompanyQuery
import team.inreok.getiserver.domain.company.query.CompanySummary
import team.inreok.getiserver.domain.job.dto.JobCreateRequest
import team.inreok.getiserver.domain.job.dto.JobDetailResponse
import team.inreok.getiserver.domain.job.dto.JobSearchResponse
import team.inreok.getiserver.domain.job.dto.JobSort
import team.inreok.getiserver.domain.job.dto.JobStatusUpdateRequest
import team.inreok.getiserver.domain.job.dto.JobSummaryResponse
import team.inreok.getiserver.domain.job.dto.JobUpdateRequest
import team.inreok.getiserver.domain.job.dto.PublicJobStatus
import team.inreok.getiserver.domain.job.entity.Job
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.entity.type.PostingType
import team.inreok.getiserver.domain.job.exception.JobCompanyNotFoundException
import team.inreok.getiserver.domain.job.exception.JobNotFoundException
import team.inreok.getiserver.domain.job.exception.JobNotVisibleException
import team.inreok.getiserver.domain.job.exception.JobStatusTransitionInvalidException
import team.inreok.getiserver.domain.job.exception.JobValidationFailedException
import team.inreok.getiserver.domain.job.repository.JobRepository
import team.inreok.getiserver.domain.job.service.JobService
import team.inreok.getiserver.domain.job.service.toTitlePattern
import team.inreok.getiserver.domain.job.service.validateCommon
import team.inreok.getiserver.domain.job.service.validateForPublish
import java.time.LocalDateTime

@Service
class JobServiceImpl(
    private val jobRepository: JobRepository,
    private val companyQuery: CompanyQuery,
) : JobService {
    @Transactional
    override fun create(
        request: JobCreateRequest,
        createdByMemberId: Long,
    ): JobDetailResponse {
        if (request.status != JobStatus.DRAFT && request.status != JobStatus.PUBLISHED) {
            throw JobValidationFailedException("공고는 DRAFT 또는 PUBLISHED 상태로만 등록할 수 있습니다.")
        }

        val title = request.title.trim()
        val company = requireActiveCompany(request.companyId)

        val job =
            Job(
                companyId = request.companyId,
                type = request.postingType,
                applicationMethod = request.applicationMethod,
                title = title,
                status = request.status,
                firstComeServed = request.firstComeServed,
            ).apply {
                this.createdByMemberId = createdByMemberId
                bodyMarkdown = request.content
                externalUrl = request.externalUrl
                recruitmentStartedAt = request.startDate
                recruitmentEndedAt = request.endDate
                targetGrade = request.targetGrade
                capacity = request.capacity
            }

        validateCommon(job)
        if (request.status == JobStatus.PUBLISHED) {
            validateForPublish(job)
            job.publishedAt = LocalDateTime.now()
        }

        // @CreationTimestamp/@UpdateTimestamp는 Flush 시점에 채워진다. 응답의 createdAt/updatedAt이
        // null로 나가지 않도록 저장과 동시에 Flush한다(CompanyServiceImpl.create와 같은 이유).
        return JobDetailResponse.from(jobRepository.saveAndFlush(job), company)
    }

    @Transactional
    override fun update(
        jobId: Long,
        request: JobUpdateRequest,
    ): JobDetailResponse {
        val job = findNotDeleted(jobId)

        // 전달하지 않았거나 null인 Field는 여기서 아무 일도 하지 않으므로 기존 값이 유지된다.
        job.apply {
            request.title?.trim()?.let {
                if (it.isEmpty()) throw JobValidationFailedException("공고 제목은 비어 있을 수 없습니다.")
                title = it
            }
            request.content?.let { bodyMarkdown = it }
            request.externalUrl?.let { externalUrl = it }
            request.startDate?.let { recruitmentStartedAt = it }
            request.endDate?.let { recruitmentEndedAt = it }
            request.targetGrade?.let { targetGrade = it }
            request.capacity?.let { capacity = it }
            request.firstComeServed?.let { firstComeServed = it }
        }

        validateCommon(job)
        // 이미 공개된 공고라면 수정 후에도 게시 조건을 계속 만족해야 한다. 그렇지 않으면 본문이
        // 비어 있거나 지원 URL이 없는 공고가 공개 목록에 남는다.
        if (job.status == JobStatus.PUBLISHED) validateForPublish(job)

        // @UpdateTimestamp가 Flush 시점에 갱신되므로 응답에 낡은 updatedAt이 담기지 않도록 먼저 Flush한다.
        jobRepository.flush()
        return JobDetailResponse.from(job, findCompanySummary(job.companyId))
    }

    @Transactional
    override fun changeStatus(
        jobId: Long,
        request: JobStatusUpdateRequest,
    ): JobDetailResponse {
        val job = findNotDeleted(jobId)
        val target = request.status
        if (target !in allowedTransitions(job.status)) {
            throw JobStatusTransitionInvalidException(job.status, target)
        }

        val now = LocalDateTime.now()
        when (target) {
            JobStatus.PUBLISHED -> {
                validateForPublish(job)
                job.publishedAt = now
            }

            JobStatus.CLOSED -> {
                job.closedAt = now
            }

            // Soft Delete: 실제 행을 지우지 않아 북마크와 지원 이력이 보존된다. status와 deletedAt을
            // 같은 Transaction에서 함께 바꿔 두 값이 어긋나지 않게 한다.
            JobStatus.DELETED -> {
                job.deletedAt = now
            }

            JobStatus.DRAFT -> {
                Unit
            }
        }
        job.status = target

        jobRepository.flush()
        return JobDetailResponse.from(job, findCompanySummary(job.companyId))
    }

    @Transactional(readOnly = true)
    override fun getForAdmin(jobId: Long): JobDetailResponse {
        // 관리자는 삭제 이력까지 확인해야 하므로 deletedAt 조건 없이 조회한다.
        val job = jobRepository.findById(jobId).orElseThrow { JobNotFoundException(jobId) }
        return JobDetailResponse.from(job, findCompanySummary(job.companyId))
    }

    // 조회수를 증가시키므로 readOnly Transaction을 쓸 수 없다.
    @Transactional
    override fun getPublicDetail(jobId: Long): JobDetailResponse {
        val job = findNotDeleted(jobId)
        if (job.status !in PublicJobStatus.ALL_VISIBLE) throw JobNotVisibleException(jobId)

        // incrementViewCount는 영속성 Context를 우회하는 UPDATE라 job.viewCount가 낡은 값으로
        // 남는다. 응답에는 이번 조회분이 반영된 값을 담아야 하므로 +1을 직접 반영한다.
        val response =
            JobDetailResponse.from(
                job = job,
                company = findCompanySummary(job.companyId),
                viewCount = job.viewCount + 1,
            )
        jobRepository.incrementViewCount(jobId)
        return response
    }

    @Transactional(readOnly = true)
    override fun searchPublic(
        query: String?,
        postingType: PostingType?,
        status: PublicJobStatus?,
        openOnly: Boolean,
        sort: JobSort,
        pageable: Pageable,
    ): JobSearchResponse {
        // 검색어를 보내지 않은 경우와 공백만 보낸 경우를 모두 "제목 조건 없음"으로 취급한다.
        val titlePattern = toTitlePattern(query)
        val statuses = status?.let { listOf(it.jobStatus) } ?: PublicJobStatus.ALL_VISIBLE

        // Pageable이 들고 온 sort는 무시하고 JobSort로만 정렬한다. Entity Field 이름이 그대로
        // 정렬 키로 노출되는 것을 막고, 항상 id DESC 보조 정렬이 붙도록 하기 위함이다.
        // page/size는 WebPageableConfig가 최대 100으로 제한한 값을 그대로 사용한다.
        val request = PageRequest.of(pageable.pageNumber, pageable.pageSize, sort.toSort())
        val page =
            jobRepository.searchPublic(
                statuses = statuses,
                titlePattern = titlePattern,
                postingType = postingType,
                openOnly = openOnly,
                now = LocalDateTime.now(),
                pageable = request,
            )

        // 항목마다 기업을 조회하면 N+1이 되므로 한 번에 가져온다.
        val companies = companyQuery.findActiveSummaries(page.content.map { it.companyId })
        return JobSearchResponse(
            content = page.content.map { JobSummaryResponse.from(it, companies[it.companyId]) },
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            first = page.isFirst,
            last = page.isLast,
        )
    }

    private fun findNotDeleted(jobId: Long): Job =
        jobRepository.findByIdAndDeletedAtIsNull(jobId) ?: throw JobNotFoundException(jobId)

    private fun requireActiveCompany(companyId: Long): CompanySummary =
        companyQuery.findActiveSummary(companyId) ?: throw JobCompanyNotFoundException(companyId)

    // 공고 등록 후 기업이 삭제될 수 있으므로 응답에서는 없어도 오류로 다루지 않고 null로 둔다.
    private fun findCompanySummary(companyId: Long): CompanySummary? = companyQuery.findActiveSummary(companyId)

    private fun allowedTransitions(current: JobStatus): Set<JobStatus> =
        when (current) {
            JobStatus.DRAFT -> setOf(JobStatus.PUBLISHED, JobStatus.DELETED)

            JobStatus.PUBLISHED -> setOf(JobStatus.CLOSED, JobStatus.DELETED)

            JobStatus.CLOSED -> setOf(JobStatus.DELETED)

            // 삭제된 공고의 복구는 명세에 확정되지 않아 허용하지 않는다. findNotDeleted가 먼저
            // 걸러내므로 실제로는 도달하지 않지만, 전이표를 한곳에서 읽을 수 있게 남겨둔다.
            JobStatus.DELETED -> emptySet()
        }
}
