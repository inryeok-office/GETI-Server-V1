package team.inreok.getiserver.domain.job.service.impl

import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.company.query.CompanyQuery
import team.inreok.getiserver.domain.company.query.CompanySummary
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.link.FileLinkPort
import team.inreok.getiserver.domain.file.link.FileSnapshot
import team.inreok.getiserver.domain.job.access.JobAiAnalysisAccessor
import team.inreok.getiserver.domain.job.access.JobApplicationEligibilityAccessSnapshot
import team.inreok.getiserver.domain.job.access.JobApplicationEligibilityAccessor
import team.inreok.getiserver.domain.job.access.JobBookmarkAccessor
import team.inreok.getiserver.domain.job.access.canViewJobFiles
import team.inreok.getiserver.domain.job.dto.JobAdminDetailResponse
import team.inreok.getiserver.domain.job.dto.JobAdminListItemResponse
import team.inreok.getiserver.domain.job.dto.JobAdminListResponse
import team.inreok.getiserver.domain.job.dto.JobCreateRequest
import team.inreok.getiserver.domain.job.dto.JobDetailResponse
import team.inreok.getiserver.domain.job.dto.JobFileResponse
import team.inreok.getiserver.domain.job.dto.JobManagerResponse
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
import team.inreok.getiserver.domain.job.service.escapeLikePattern
import team.inreok.getiserver.domain.job.service.validateCommon
import team.inreok.getiserver.domain.job.service.validateForPublish
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.query.JobManagerSnapshotQueryPort
import team.inreok.getiserver.domain.member.query.MemberRoleQueryPort
import team.inreok.getiserver.global.discord.DiscordChannelResolver
import java.time.LocalDateTime

// Job CRUD·상태 관리·File 연동을 한 Service가 담당해(Issue #126) Public Method 개수가 detekt
// 기본 TooManyFunctions 임계값을 넘는다. ProgramServiceImpl이 이미 같은 방식으로 Suppress한
// 전례를 따른다.
@Suppress("TooManyFunctions")
@Service
class JobServiceImpl(
    private val jobRepository: JobRepository,
    private val companyQuery: CompanyQuery,
    private val eventPublisher: ApplicationEventPublisher,
    private val discordChannelResolver: DiscordChannelResolver,
    private val jobAiAnalysisAccessor: JobAiAnalysisAccessor,
    private val jobApplicationEligibilityAccessor: JobApplicationEligibilityAccessor,
    private val jobBookmarkAccessor: JobBookmarkAccessor,
    private val fileLinkPort: FileLinkPort,
    private val memberRoleQueryPort: MemberRoleQueryPort,
    private val jobManagerSnapshotQueryPort: JobManagerSnapshotQueryPort,
) : JobService {
    @Transactional(readOnly = true)
    override fun listForAdmin(
        query: String?,
        status: JobStatus?,
        pageable: Pageable,
        requesterId: Long,
    ): JobAdminListResponse {
        val normalizedQuery = query?.trim()?.takeIf { it.isNotEmpty() }?.let(::escapeLikePattern)
        // The repository query defines a stable createdAt/id order. Do not append a client Sort
        // after that ORDER BY because it would either be ineffective or fail for unsupported fields.
        val queryPageable = PageRequest.of(pageable.pageNumber, pageable.pageSize)
        val page = jobRepository.searchForAdmin(normalizedQuery, status, queryPageable)
        val companies =
            companyQuery.findActiveSummaries(
                companyIds = page.content.map { it.companyId }.toSet(),
                requesterId = requesterId,
            )
        val managers = findManagers(page.content)
        return JobAdminListResponse(
            content =
                page.content.map { job ->
                    JobAdminListItemResponse.from(job, companies[job.companyId], managers[job.id])
                },
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            first = page.isFirst,
            last = page.isLast,
        )
    }

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
                location = request.location
                employmentType = request.employmentType
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
        val jobId = requireNotNull(saved.id)

        // 저장 이후에만 연결한다 -- 연결 대상(ownerId)으로 쓸 jobId가 저장 전에는 없다
        // (ProgramServiceImpl.create와 동일한 순서). 소유권·목적·상태·개수 검증은
        // FileLinkPort.validateAndLink가 수행하며, 거부되면 예외가 Transaction을 되돌려 공고도
        // 함께 만들어지지 않는다.
        if (request.fileIds.isNotEmpty()) {
            fileLinkPort.validateAndLink(
                requesterId = createdByMemberId,
                fileIds = request.fileIds,
                purpose = FilePurpose.JOB_ATTACHMENT,
                ownerId = jobId,
            )
        }

        // Transaction Commit 이후에만 실제로 전달된다(@TransactionalEventListener). 색인 동기화가
        // 실패해도 이 등록 자체를 Rollback하지 않는다(Issue #69, PostgreSQL이 원본 유지).
        eventPublisher.publishEvent(JobChangedEvent(jobId))
        if (saved.status == JobStatus.PUBLISHED) {
            eventPublisher.publishEvent(JobDiscordEvent(jobId, JobDiscordAction.PUBLISHED))
        }
        return JobDetailResponse.from(
            saved,
            company,
            aiAnalysis = jobAiAnalysisAccessor.findSnapshot(jobId),
            application = applicationEligibilityOf(jobApplicationEligibilityAccessor, jobId, createdByMemberId),
            bookmarked = bookmarkedOf(jobBookmarkAccessor, jobId, createdByMemberId),
            files = jobFilesFor(saved, jobId, createdByMemberId),
        )
    }

    @Transactional
    override fun update(
        jobId: Long,
        request: JobUpdateRequest,
        requesterId: Long,
    ): JobDetailResponse {
        val job = findNotDeleted(jobId)

        applyUpdatableFields(job, request)
        // Discord 채널 Key만 같이 옮기지 않는 이유는 허용 목록 검증이 Service의 설정 의존성을
        // 필요로 하기 때문이다 -- 순수 함수로 분리한 applyUpdatableFields에는 넣을 수 없다.
        request.discordChannelKey?.let { job.discordChannelKey = requireAllowedDiscordChannelKey(it) }
        applyFileIdsUpdate(jobId, requesterId, request)

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
            bookmarked = bookmarkedOf(jobBookmarkAccessor, jobId, requesterId),
            files = jobFilesFor(job, jobId, requesterId),
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
                // Storage Binary는 지우지 않는다(FileLinkPort.unlinkAllOf KDoc 참고) -- 연결만
                // 해제해 이 공고가 더 이상 첨부파일을 "쓰고 있지 않은" 상태로 만든다. 물리 삭제는
                // Cleanup(Phase 5)이 보존 기간을 보고 판단한다(Issue #126, ProgramServiceImpl과
                // 동일한 결정). findNotDeleted가 이미 이 Job Row를 조회한 같은 Transaction 안에서
                // 호출해 새 Transaction을 열지 않는다.
                fileLinkPort.unlinkAllOf(FileOwnerType.JOB, jobId)
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
            bookmarked = bookmarkedOf(jobBookmarkAccessor, jobId, requesterId),
            files = jobFilesFor(job, jobId, requesterId),
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
    ): JobAdminDetailResponse {
        // 관리자는 삭제 이력까지 확인해야 하므로 deletedAt 조건 없이 조회한다.
        val job = jobRepository.findById(jobId).orElseThrow { JobNotFoundException(jobId) }
        val detail =
            JobDetailResponse.from(
                job,
                findCompanySummary(job.companyId, requesterId),
                aiAnalysis = jobAiAnalysisAccessor.findSnapshot(jobId),
                application = applicationEligibilityOf(jobApplicationEligibilityAccessor, jobId, requesterId),
                bookmarked = bookmarkedOf(jobBookmarkAccessor, jobId, requesterId),
                files = jobFilesFor(job, jobId, requesterId),
            )
        return JobAdminDetailResponse.from(detail, findManagers(listOf(job))[job.id])
    }

    private fun findManagers(jobs: Collection<Job>): Map<Long, JobManagerResponse> {
        val memberIds = jobs.flatMap { listOfNotNull(it.managerMemberId, it.createdByMemberId) }.toSet()
        val snapshots = jobManagerSnapshotQueryPort.findAllByIds(memberIds)
        return jobs
            .mapNotNull { job ->
                val manager =
                    sequenceOf(job.managerMemberId, job.createdByMemberId)
                        .filterNotNull()
                        .mapNotNull { memberId -> snapshots[memberId]?.let(JobManagerResponse::from) }
                        .firstOrNull()
                job.id?.let { jobId -> manager?.let { jobId to it } }
            }.toMap()
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
                bookmarked = bookmarkedOf(jobBookmarkAccessor, jobId, requesterId),
                files = jobFilesFor(job, jobId, requesterId),
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

    /**
     * `fileIds`를 전달하지 않으면(null) 기존 첨부파일을 그대로 둔다. 전달하면(빈 List 포함) 그
     * 목록을 최종 상태로 취급해 기존 연결을 모두 해제한 뒤 다시 연결한다(`JobUpdateRequest` KDoc,
     * `ProgramServiceImpl.applyFileIdsUpdate`와 동일한 방식). 항상 먼저 `unlinkAllOf`로 끊는
     * 이유는 `FileLinkPortImpl.verifyCount`가 "현재 연결된 개수 + 새로 연결할 개수"로 상한을
     * 검사하기 때문이다 -- 먼저 끊지 않으면 교체가 아니라 추가로 계산되어 상한을 잘못 초과 판정한다.
     */
    private fun applyFileIdsUpdate(
        jobId: Long,
        requesterId: Long,
        request: JobUpdateRequest,
    ) {
        val fileIds = request.fileIds ?: return
        fileLinkPort.unlinkAllOf(FileOwnerType.JOB, jobId)
        if (fileIds.isNotEmpty()) {
            fileLinkPort.validateAndLink(
                requesterId = requesterId,
                fileIds = fileIds,
                purpose = FilePurpose.JOB_ATTACHMENT,
                ownerId = jobId,
            )
        }
    }

    /**
     * DRAFT 상태에서 조회 권한이 없는 요청자에게는 빈 목록을 반환한다 -- `JobFileAccessChecker`의
     * 다운로드 권한 판정과 같은 규칙(`canViewJobFiles`)을 써야 상세 응답이 실제 다운로드 가능
     * 여부보다 더 많은 첨부파일 Metadata(파일명 등)를 흘리지 않는다.
     *
     * DEVELOPER 판정은 `JobFileAccessChecker.canDownload`와 동일하게 [memberRoleQueryPort]로
     * DB에서 직접 조회한다(`ProgramServiceImpl.programFilesFor`와 동일한 이유 -- 두 판정 경로가
     * 서로 다른 Role 출처를 쓰면 토큰 발급 이후 역할이 바뀐 사용자에게 다운로드 권한과 상세 응답의
     * 파일 목록 노출 여부가 어긋날 수 있다).
     */
    private fun jobFilesFor(
        job: Job,
        jobId: Long,
        requesterId: Long,
    ): List<JobFileResponse> {
        val isDeveloper = { RoleType.DEVELOPER in memberRoleQueryPort.findRoles(requesterId) }
        return if (canViewJobFiles(job, requesterId, isDeveloper)) {
            fileLinkPort.linkedFilesOf(FileOwnerType.JOB, jobId).map { it.toResponse() }
        } else {
            emptyList()
        }
    }

    private fun FileSnapshot.toResponse(): JobFileResponse =
        JobFileResponse(
            fileId = fileId,
            originalName = originalName,
            contentType = contentType,
            size = size,
            downloadUrl = "/api/v1/files/$fileId/download",
        )
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

// JobServiceImpl의 Method 개수를 detekt TooManyFunctions 한도 안에서 유지하기 위해 순수 함수로
// 분리했다(위 applicationEligibilityOf와 같은 이유, Issue #171). [JobBookmarkAccessor]는 북마크한
// jobId만 돌려주므로(Interface 주석) 결과 Set에 jobId가 있는지로 판정한다.
private fun bookmarkedOf(
    accessor: JobBookmarkAccessor,
    jobId: Long,
    requesterId: Long,
): Boolean = jobId in accessor.findAllByJobIds(setOf(jobId), requesterId)

// JobServiceImpl의 Method 개수를 detekt TooManyFunctions 한도 안에서 유지하기 위해 분리했다
// (위 applicationEligibilityOf와 같은 이유). 전달하지 않았거나 null인 Field는 여기서 아무 일도
// 하지 않으므로 기존 값이 유지된다. 값을 비우는 기능은 이번 범위가 아니다(JobUpdateRequest 주석).
// Discord 채널 Key는 허용 목록 검증이 필요해 호출 측(JobServiceImpl.update)에 남겨 둔다.
private fun applyUpdatableFields(
    job: Job,
    request: JobUpdateRequest,
) {
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
        request.location?.let { location = it }
        request.employmentType?.let { employmentType = it }
        request.firstComeServed?.let { firstComeServed = it }
    }
}
