package team.inreok.getiserver.domain.collector.service.impl

import org.slf4j.LoggerFactory
import org.springframework.core.task.TaskExecutor
import org.springframework.stereotype.Service
import team.inreok.getiserver.domain.collector.entity.CollectionRun
import team.inreok.getiserver.domain.collector.entity.CollectionRunError
import team.inreok.getiserver.domain.collector.entity.JobSource
import team.inreok.getiserver.domain.collector.entity.type.CollectionRunStatus
import team.inreok.getiserver.domain.collector.entity.type.CollectorAction
import team.inreok.getiserver.domain.collector.entity.type.JobDataQualityStatus
import team.inreok.getiserver.domain.collector.entity.type.JobSourceApprovalStatus
import team.inreok.getiserver.domain.collector.exception.CollectorAlreadyRunningException
import team.inreok.getiserver.domain.collector.exception.JobSourceNotFoundException
import team.inreok.getiserver.domain.collector.exception.SourceNotApprovedException
import team.inreok.getiserver.domain.collector.exception.SourceNotConfiguredException
import team.inreok.getiserver.domain.collector.notification.discord.CollectionRunSummaryEmbedInput
import team.inreok.getiserver.domain.collector.notification.service.CollectionRunNotificationSender
import team.inreok.getiserver.domain.collector.notification.service.JobNotificationService
import team.inreok.getiserver.domain.collector.notification.service.JobNotificationTrigger
import team.inreok.getiserver.domain.collector.provider.CollectorCollectionContext
import team.inreok.getiserver.domain.collector.provider.CollectorProviderException
import team.inreok.getiserver.domain.collector.provider.NormalizedCollectedJob
import team.inreok.getiserver.domain.collector.provider.ProviderRegistry
import team.inreok.getiserver.domain.collector.repository.CollectionRunErrorRepository
import team.inreok.getiserver.domain.collector.repository.CollectionRunRepository
import team.inreok.getiserver.domain.collector.repository.JobSourceRepository
import team.inreok.getiserver.domain.collector.service.CollectorExecutionService
import team.inreok.getiserver.domain.company.external.CompanyExternalImportCommand
import team.inreok.getiserver.domain.company.external.CompanyExternalImportUseCase
import team.inreok.getiserver.domain.job.upsert.CollectedJobUpsertCommand
import team.inreok.getiserver.domain.job.upsert.CollectedJobUpsertUseCase
import team.inreok.getiserver.domain.job.upsert.JobImportOutcome
import java.time.LocalDateTime

/**
 * 수집 실행 Orchestrator다. 외부 호출(Provider.collect)과 DB Transaction을 분리하기 위해 이
 * Class 자체에는 `@Transactional`을 붙이지 않는다 — 각 저장 호출은 Spring Data
 * `SimpleJpaRepository`가 제공하는 자체 Transaction으로 짧게 끝난다(docs 17. Transaction
 * Convention). 동일 수집 중복 실행 방지는 ShedLock 같은 분산 Lock 없이, 같은 Source에 이미
 * PENDING/RUNNING CollectionRun이 있는지로 판정한다(단일 Instance 배포 기준, 최종 보고 참고).
 */
@Service
class CollectorExecutionServiceImpl(
    private val jobSourceRepository: JobSourceRepository,
    private val collectionRunRepository: CollectionRunRepository,
    private val collectionRunErrorRepository: CollectionRunErrorRepository,
    private val providerRegistry: ProviderRegistry,
    private val collectedJobUpsertUseCase: CollectedJobUpsertUseCase,
    private val companyExternalImportUseCase: CompanyExternalImportUseCase,
    private val jobNotificationService: JobNotificationService,
    private val collectionRunNotificationSender: CollectionRunNotificationSender,
    private val collectorTaskExecutor: TaskExecutor,
) : CollectorExecutionService {
    override fun runDailyCollection() {
        val targets =
            jobSourceRepository.findAllByOrderBySourceCodeAsc().filter {
                it.enabled && it.approvalStatus == JobSourceApprovalStatus.READY
            }

        // Provider 하나의 예외가 다른 Provider 실행을 막지 않도록 각 Source를 독립적으로 처리한다.
        targets.forEach { source ->
            runCatching { collectForScheduledSource(source) }
                .onFailure { ex -> log.error("Collector 실행 중 처리되지 않은 오류(sourceId={})", source.id, ex) }
        }
    }

    override fun triggerManual(
        action: CollectorAction,
        sourceIds: List<Long>,
    ): List<CollectionRun> {
        // 하나라도 검증에 실패하면 어떤 실행도 만들지 않는다(부분 접수 없음).
        val sources = sourceIds.map(::requireRunnable)

        val startedAt = LocalDateTime.now()
        val runs =
            sources.map { source ->
                collectionRunRepository.saveAndFlush(
                    CollectionRun(sourceId = requireNotNull(source.id), action = action, startedAt = startedAt),
                )
            }

        // 요청 Thread는 여기서 반환한다(202 Accepted) — 실제 Provider 호출은 별도 Thread에서 진행한다.
        runs.zip(sources).forEach { (run, source) ->
            collectorTaskExecutor.execute {
                runCatching { executeRun(run, source) }
                    .onFailure { ex -> log.error("수동 수집 실행 중 처리되지 않은 오류(runId={})", run.id, ex) }
            }
        }
        return runs
    }

    // 조건(수집원 존재·승인·설정·중복 실행)마다 서로 다른 Error Code로 구분해야 하므로 분기마다 던진다.
    @Suppress("ThrowsCount")
    private fun requireRunnable(sourceId: Long): JobSource {
        val source = jobSourceRepository.findById(sourceId).orElseThrow { JobSourceNotFoundException(sourceId) }
        if (source.approvalStatus != JobSourceApprovalStatus.READY) throw SourceNotApprovedException(sourceId)
        if (!providerRegistry.isConfigured(source.sourceCode)) throw SourceNotConfiguredException(sourceId)
        if (collectionRunRepository.existsBySourceIdAndStatusIn(sourceId, ACTIVE_STATUSES)) {
            throw CollectorAlreadyRunningException(sourceId)
        }
        return source
    }

    // 이미 진행 중인 실행/등록된 Provider 없음은 오류가 아니라 정상 Skip이라 가드 절로
    // 일찍 반환하는 편이 중첩 if/else보다 읽기 쉽다고 판단해 ReturnCount를 그대로 둔다.
    @Suppress("ReturnCount")
    private fun collectForScheduledSource(source: JobSource) {
        val sourceId = requireNotNull(source.id)
        if (collectionRunRepository.existsBySourceIdAndStatusIn(sourceId, ACTIVE_STATUSES)) {
            log.info("Collector 실행 Skip(이미 진행 중): sourceId={}", sourceId)
            return
        }
        if (providerRegistry.find(source.sourceCode) == null) {
            // 등록된 Provider가 없는 정상 Skip이다(실패 이력을 만들지 않는다).
            log.info("Collector 실행 Skip(등록된 Provider 없음): sourceCode={}", source.sourceCode)
            return
        }

        val run =
            collectionRunRepository.saveAndFlush(
                CollectionRun(sourceId = sourceId, action = CollectorAction.SYNC, startedAt = LocalDateTime.now()),
            )
        executeRun(run, source)
    }

    private fun executeRun(
        run: CollectionRun,
        source: JobSource,
    ) {
        run.status = CollectionRunStatus.RUNNING
        var current = collectionRunRepository.saveAndFlush(run)

        val provider = providerRegistry.find(source.sourceCode)
        if (provider == null) {
            // 검증 시점 이후 Provider가 사라지는 경우는 사실상 없지만, 방어적으로 실패 처리한다.
            finishAsProviderFailure(current, source, CODE_PROVIDER_NOT_FOUND, "등록된 Provider를 찾을 수 없습니다.")
            return
        }

        val context = CollectorCollectionContext(requestedAt = current.startedAt, since = source.lastCollectedAt)
        val result =
            try {
                provider.collect(context)
            } catch (ex: CollectorProviderException) {
                finishAsProviderFailure(current, source, ex.code, ex.message ?: ex.code)
                return
            }

        var successCount = 0
        var failureCount = 0
        var partialQualityCount = 0
        // finishAsCompleted가 source.lastSuccessAt을 갱신하기 전에 판정해야 한다 — "이 Provider의
        // 첫 성공 Run인지"가 초기 수집 알림 억제 정책(app.discord.job-notification.notify-initial-import)의
        // 기준이다(Issue #62 확장 범위).
        val isInitialImport = source.lastSuccessAt == null

        result.errors.forEach { itemError ->
            recordError(
                requireNotNull(current.id),
                itemError.externalJobId,
                itemError.code,
                itemError.message,
                emptyList(),
            )
            failureCount++
        }

        result.jobs.forEach { job ->
            if (job.dataQualityStatus == JobDataQualityStatus.PARTIAL) partialQualityCount++
            if (upsertJob(requireNotNull(current.id), source, job, isInitialImport)) successCount++ else failureCount++
        }

        current = finishAsCompleted(current, source, successCount, failureCount, partialQualityCount)
    }

    /**
     * 저장 성공이면 true, CollectionRunError를 기록하고 실패로 집계했으면 false. Job Upsert가
     * 어떤 예외로 실패하든(BusinessException, DataIntegrityViolationException 등) 이 공고 하나만
     * 실패로 집계하고 나머지 공고 처리를 계속해야 하므로 의도적으로 RuntimeException을 넓게 잡는다.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun upsertJob(
        runId: Long,
        source: JobSource,
        job: NormalizedCollectedJob,
        isInitialImport: Boolean,
    ): Boolean =
        try {
            val company =
                companyExternalImportUseCase.findOrCreateExternal(
                    CompanyExternalImportCommand(companyName = job.companyName, sourceCode = source.sourceCode.name),
                )
            val result =
                collectedJobUpsertUseCase.upsert(
                    CollectedJobUpsertCommand(
                        companyId = company.companyId,
                        sourceName = source.sourceCode.name,
                        externalJobId = job.externalJobId,
                        title = job.title,
                        content = job.content,
                        externalUrl = job.externalUrl,
                        startDate = job.startDate,
                        endDate = job.endDate,
                        publish = job.dataQualityStatus == JobDataQualityStatus.COMPLETE,
                    ),
                )
            if (result.outcome == JobImportOutcome.CREATED) {
                notifyNewJob(result.jobId, source, job, isInitialImport)
            }
            true
        } catch (ex: RuntimeException) {
            recordError(runId, job.externalJobId, CODE_UPSERT_FAILED, "공고 저장에 실패했습니다.", job.missingFields)
            log.warn("수집 공고 Job 반영 실패: sourceCode={}, externalJobId={}", source.sourceCode, job.externalJobId, ex)
            false
        }

    // Discord 알림 실패가 이미 성공한 Job/Company 저장의 성공 처리(successCount)에 영향을 주면
    // 안 되므로, 여기서 발생하는 어떤 예외도 Job 반영 결과와 분리해 삼킨다(로그만 남긴다).
    @Suppress("TooGenericExceptionCaught")
    private fun notifyNewJob(
        jobId: Long,
        source: JobSource,
        job: NormalizedCollectedJob,
        isInitialImport: Boolean,
    ) {
        try {
            jobNotificationService.enqueueIfEligible(
                JobNotificationTrigger(
                    jobId = jobId,
                    sourceCode = source.sourceCode.name,
                    sourceDisplayName = source.name,
                    title = job.title,
                    companyName = job.companyName,
                    externalUrl = job.externalUrl,
                    recruitmentEndedAt = job.endDate,
                ),
                isInitialImport = isInitialImport,
            )
        } catch (ex: Exception) {
            log.warn("Discord 신규 공고 알림 등록 중 오류(jobId={})", jobId, ex)
        }
    }

    private fun recordError(
        runId: Long,
        externalJobId: String?,
        code: String,
        message: String,
        missingFields: List<String>,
    ) {
        val error =
            CollectionRunError(runId = runId, code = code, message = message, occurredAt = LocalDateTime.now()).apply {
                this.externalJobId = externalJobId
                this.missingFields = missingFields.takeIf { it.isNotEmpty() }?.joinToString(",")
            }
        collectionRunErrorRepository.save(error)
    }

    private fun finishAsProviderFailure(
        run: CollectionRun,
        source: JobSource,
        code: String,
        message: String,
    ) {
        recordError(requireNotNull(run.id), externalJobId = null, code = code, message = message, emptyList())
        run.status = CollectionRunStatus.FAILED
        run.failureCount = 1
        run.totalCount = 1
        run.finishedAt = LocalDateTime.now()
        val saved = collectionRunRepository.saveAndFlush(run)

        source.lastCollectedAt = saved.finishedAt
        source.lastFailureAt = saved.finishedAt
        source.lastError = message
        jobSourceRepository.saveAndFlush(source)

        notifyRunCompleted(saved, source, failureReason = message)
    }

    private fun finishAsCompleted(
        run: CollectionRun,
        source: JobSource,
        successCount: Int,
        failureCount: Int,
        partialQualityCount: Int,
    ): CollectionRun {
        run.successCount = successCount
        run.failureCount = failureCount
        run.partialQualityCount = partialQualityCount
        run.totalCount = successCount + failureCount
        run.status =
            when {
                failureCount == 0 -> CollectionRunStatus.SUCCESS
                successCount == 0 && failureCount > 0 -> CollectionRunStatus.FAILED
                else -> CollectionRunStatus.PARTIAL_SUCCESS
            }
        run.finishedAt = LocalDateTime.now()
        val saved = collectionRunRepository.saveAndFlush(run)

        source.lastCollectedAt = saved.finishedAt
        if (saved.status != CollectionRunStatus.FAILED) source.lastSuccessAt = saved.finishedAt
        if (saved.status != CollectionRunStatus.SUCCESS) {
            source.lastFailureAt = saved.finishedAt
            source.lastError = "일부 또는 전체 공고 처리에 실패했습니다(failureCount=$failureCount)."
        }
        jobSourceRepository.saveAndFlush(source)

        notifyRunCompleted(saved, source, failureReason = null)
        return saved
    }

    // Discord 알림 실패가 이미 확정된 Run 결과·Source 상태 저장에 영향을 주면 안 되므로, 여기서
    // 발생하는 어떤 예외도 삼킨다(로그만 남긴다). 개별 신규 공고 알림과 달리 DB 재시도 대상이
    // 아니다(Best Effort, 최종 보고 참고).
    @Suppress("TooGenericExceptionCaught")
    private fun notifyRunCompleted(
        run: CollectionRun,
        source: JobSource,
        failureReason: String?,
    ) {
        try {
            collectionRunNotificationSender.notify(
                CollectionRunSummaryEmbedInput(
                    runId = requireNotNull(run.id),
                    sourceDisplayName = source.name,
                    action = run.action,
                    status = run.status,
                    totalCount = run.totalCount,
                    successCount = run.successCount,
                    failureCount = run.failureCount,
                    partialQualityCount = run.partialQualityCount,
                    startedAt = run.startedAt,
                    finishedAt = requireNotNull(run.finishedAt),
                    failureReason = failureReason,
                ),
            )
        } catch (ex: Exception) {
            log.warn("Collection Run 요약 알림 전송 중 오류(runId={})", run.id, ex)
        }
    }

    private companion object {
        val ACTIVE_STATUSES = listOf(CollectionRunStatus.PENDING, CollectionRunStatus.RUNNING)
        const val CODE_PROVIDER_NOT_FOUND = "COLLECTOR_PROVIDER_NOT_FOUND"
        const val CODE_UPSERT_FAILED = "COLLECTED_JOB_UPSERT_FAILED"
        private val log = LoggerFactory.getLogger(CollectorExecutionServiceImpl::class.java)
    }
}
