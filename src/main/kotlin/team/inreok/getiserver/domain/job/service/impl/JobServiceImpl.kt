package team.inreok.getiserver.domain.job.service.impl

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.company.query.CompanyQuery
import team.inreok.getiserver.domain.company.query.CompanySummary
import team.inreok.getiserver.domain.job.access.JobAiAnalysisAccessor
import team.inreok.getiserver.domain.job.access.JobApplicationEligibilityAccessSnapshot
import team.inreok.getiserver.domain.job.access.JobApplicationEligibilityAccessor
import team.inreok.getiserver.domain.job.dto.JobCreateRequest
import team.inreok.getiserver.domain.job.dto.JobDetailResponse
import team.inreok.getiserver.domain.job.dto.JobStatusUpdateRequest
import team.inreok.getiserver.domain.job.dto.JobUpdateRequest
import team.inreok.getiserver.domain.job.entity.Job
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.event.JobChangedEvent
import team.inreok.getiserver.domain.job.event.JobDiscordAction
import team.inreok.getiserver.domain.job.event.JobDiscordEvent
import team.inreok.getiserver.domain.job.exception.JobCompanyNotFoundException
import team.inreok.getiserver.domain.job.exception.JobDiscordChannelNotAllowedException
import team.inreok.getiserver.domain.job.exception.JobNotFoundException
import team.inreok.getiserver.domain.job.exception.JobNotVisibleException
import team.inreok.getiserver.domain.job.exception.JobStatusTransitionInvalidException
import team.inreok.getiserver.domain.job.exception.JobValidationFailedException
import team.inreok.getiserver.domain.job.repository.JobRepository
import team.inreok.getiserver.domain.job.service.JobService
import team.inreok.getiserver.domain.job.service.PUBLIC_VISIBLE_STATUSES
import team.inreok.getiserver.domain.job.service.validateCommon
import team.inreok.getiserver.domain.job.service.validateForPublish
import team.inreok.getiserver.global.discord.DiscordChannelResolver
import java.time.LocalDateTime

@Service
class JobServiceImpl(
    private val jobRepository: JobRepository,
    private val companyQuery: CompanyQuery,
    private val eventPublisher: ApplicationEventPublisher,
    private val discordChannelResolver: DiscordChannelResolver,
    private val jobAiAnalysisAccessor: JobAiAnalysisAccessor,
    private val jobApplicationEligibilityAccessor: JobApplicationEligibilityAccessor,
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
        val company = requireActiveCompany(request.companyId, createdByMemberId)
        val discordChannelKey = requireAllowedDiscordChannelKey(request.discordChannelKey)

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
                this.discordChannelKey = discordChannelKey
            }

        validateCommon(job)
        if (request.status == JobStatus.PUBLISHED) {
            validateForPublish(job)
            job.publishedAt = LocalDateTime.now()
        }

        // @CreationTimestamp/@UpdateTimestamp는 Flush 시점에 채워진다. 응답의 createdAt/updatedAt이
        // null로 나가지 않도록 저장과 동시에 Flush한다(CompanyServiceImpl.create와 같은 이유).
        val saved = jobRepository.saveAndFlush(job)
        // Transaction Commit 이후에만 실제로 전달된다(@TransactionalEventListener). 색인 동기화가
        // 실패해도 이 등록 자체를 Rollback하지 않는다(Issue #69, PostgreSQL이 원본 유지).
        eventPublisher.publishEvent(JobChangedEvent(requireNotNull(saved.id)))
        if (saved.status == JobStatus.PUBLISHED) {
            eventPublisher.publishEvent(JobDiscordEvent(requireNotNull(saved.id), JobDiscordAction.PUBLISHED))
        }
        return JobDetailResponse.from(
            saved,
            company,
            aiAnalysis = jobAiAnalysisAccessor.findSnapshot(requireNotNull(saved.id)),
            application =
                applicationEligibilityOf(
                    jobApplicationEligibilityAccessor,
                    requireNotNull(saved.id),
                    createdByMemberId,
                ),
        )
    }

    @Transactional
    override fun update(
        jobId: Long,
        request: JobUpdateRequest,
        requesterId: Long,
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
            request.discordChannelKey?.let { discordChannelKey = requireAllowedDiscordChannelKey(it) }
        }

        validateCommon(job)
        // 이미 공개된 공고라면 수정 후에도 게시 조건을 계속 만족해야 한다. 그렇지 않으면 본문이
        // 비어 있거나 지원 URL이 없는 공고가 공개 목록에 남는다.
        if (job.status == JobStatus.PUBLISHED) validateForPublish(job)

        // @UpdateTimestamp가 Flush 시점에 갱신되므로 응답에 낡은 updatedAt이 담기지 않도록 먼저 Flush한다.
        jobRepository.flush()
        eventPublisher.publishEvent(JobChangedEvent(jobId))
        // DRAFT는 Discord에 아직 메시지가 없어(§4.2) 발행하지 않는다 -- 발행하면 Worker가
        // MISSING_DISCORD_MESSAGE_ID로 실패 처리할 Row만 쌓인다.
        if (job.status in PUBLIC_VISIBLE_STATUSES) {
            eventPublisher.publishEvent(JobDiscordEvent(jobId, JobDiscordAction.UPDATED))
        }
        return JobDetailResponse.from(
            job,
            findCompanySummary(job.companyId, requesterId),
            aiAnalysis = jobAiAnalysisAccessor.findSnapshot(jobId),
            application = applicationEligibilityOf(jobApplicationEligibilityAccessor, jobId, requesterId),
        )
    }

    @Transactional
    override fun changeStatus(
        jobId: Long,
        request: JobStatusUpdateRequest,
        requesterId: Long,
    ): JobDetailResponse {
        val job = findNotDeleted(jobId)
        val target = request.status
        if (target !in allowedTransitions(job.status)) {
            throw JobStatusTransitionInvalidException(job.status, target)
        }
        val previousStatus = job.status

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
        eventPublisher.publishEvent(JobChangedEvent(jobId))
        publishJobDiscordEventFor(jobId, target, previousStatus)
        return JobDetailResponse.from(
            job,
            findCompanySummary(job.companyId, requesterId),
            aiAnalysis = jobAiAnalysisAccessor.findSnapshot(jobId),
            application = applicationEligibilityOf(jobApplicationEligibilityAccessor, jobId, requesterId),
        )
    }

    /**
     * 상태 전이에 대응하는 Discord Event를 발행한다(§4.2). `DRAFT`였던 공고가 삭제되면
     * Discord에 지울 메시지가 없으므로 `DELETED`는 발행하지 않는다 -- Worker가
     * `MISSING_DISCORD_MESSAGE_ID`로 실패 처리할 Row만 쌓이는 것을 막는다.
     */
    private fun publishJobDiscordEventFor(
        jobId: Long,
        target: JobStatus,
        previousStatus: JobStatus,
    ) {
        val action =
            when (target) {
                JobStatus.PUBLISHED -> JobDiscordAction.PUBLISHED
                JobStatus.CLOSED -> JobDiscordAction.CLOSED
                JobStatus.DELETED -> if (previousStatus != JobStatus.DRAFT) JobDiscordAction.DELETED else null
                JobStatus.DRAFT -> null
            } ?: return
        eventPublisher.publishEvent(JobDiscordEvent(jobId, action))
    }

    @Transactional(readOnly = true)
    override fun getForAdmin(
        jobId: Long,
        requesterId: Long,
    ): JobDetailResponse {
        // 관리자는 삭제 이력까지 확인해야 하므로 deletedAt 조건 없이 조회한다.
        val job = jobRepository.findById(jobId).orElseThrow { JobNotFoundException(jobId) }
        return JobDetailResponse.from(
            job,
            findCompanySummary(job.companyId, requesterId),
            aiAnalysis = jobAiAnalysisAccessor.findSnapshot(jobId),
            application = applicationEligibilityOf(jobApplicationEligibilityAccessor, jobId, requesterId),
        )
    }

    // 조회수를 증가시키므로 readOnly Transaction을 쓸 수 없다.
    @Transactional
    override fun getPublicDetail(
        jobId: Long,
        requesterId: Long,
    ): JobDetailResponse {
        val job = findNotDeleted(jobId)
        if (job.status !in PUBLIC_VISIBLE_STATUSES) throw JobNotVisibleException(jobId)

        // incrementViewCount는 영속성 Context를 우회하는 UPDATE라 job.viewCount가 낡은 값으로
        // 남는다. 응답에는 이번 조회분이 반영된 값을 담아야 하므로 +1을 직접 반영한다.
        val response =
            JobDetailResponse.from(
                job = job,
                company = findCompanySummary(job.companyId, requesterId),
                viewCount = job.viewCount + 1,
                aiAnalysis = jobAiAnalysisAccessor.findSnapshot(jobId),
                application = applicationEligibilityOf(jobApplicationEligibilityAccessor, jobId, requesterId),
            )
        jobRepository.incrementViewCount(jobId)
        return response
    }

    private fun findNotDeleted(jobId: Long): Job =
        jobRepository.findByIdAndDeletedAtIsNull(jobId) ?: throw JobNotFoundException(jobId)

    /**
     * `null`이면 검증 없이 그대로 돌려준다 -- 채널을 지정하지 않으면 게시 시 기본 채널을 쓴다
     * (`DiscordChannelResolver.resolveJobChannelId`). 값이 있는데 허용 목록에 없으면 사용자가
     * 임의 채널을 지정하지 못하도록 즉시 거부한다(Notification 요구사항 §10).
     */
    private fun requireAllowedDiscordChannelKey(key: String?): String? {
        if (key == null) return null
        if (!discordChannelResolver.isAllowedJobChannelKey(key)) throw JobDiscordChannelNotAllowedException(key)
        return key
    }

    private fun requireActiveCompany(
        companyId: Long,
        requesterId: Long,
    ): CompanySummary =
        companyQuery.findActiveSummary(companyId, requesterId) ?: throw JobCompanyNotFoundException(companyId)

    // 공고 등록 후 기업이 삭제될 수 있으므로 응답에서는 없어도 오류로 다루지 않고 null로 둔다.
    private fun findCompanySummary(
        companyId: Long,
        requesterId: Long,
    ): CompanySummary? = companyQuery.findActiveSummary(companyId, requesterId)

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

// JobServiceImpl의 Method 개수를 detekt TooManyFunctions 한도 안에서 유지하기 위해 순수 함수로
// 분리했다(application.service.impl.computeEligibilityReason과 같은 이유). Job 상세 응답 하나에
// 필요한 단건 조회이므로 jobIds Set은 항상 원소 1개다(Issue #136,
// JobApplicationEligibilityAccessor의 Class 주석 -- Search 목록은 JobSearchServiceImpl이 여러
// jobId를 한 번에 배치로 넘긴다). 입력한 jobId는 항상 결과 Map에 담긴다(Accessor 계약).
private fun applicationEligibilityOf(
    accessor: JobApplicationEligibilityAccessor,
    jobId: Long,
    requesterId: Long,
): JobApplicationEligibilityAccessSnapshot = accessor.findAllByJobIds(setOf(jobId), requesterId).getValue(jobId)
