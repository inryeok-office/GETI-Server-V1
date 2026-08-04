package team.inreok.getiserver.domain.collector.service.impl

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import team.inreok.getiserver.domain.collector.entity.CollectionRun
import team.inreok.getiserver.domain.collector.entity.CollectionRunError
import team.inreok.getiserver.domain.collector.entity.JobSource
import team.inreok.getiserver.domain.collector.entity.type.CollectionRunStatus
import team.inreok.getiserver.domain.collector.entity.type.JobDataQualityStatus
import team.inreok.getiserver.domain.collector.entity.type.JobSourceApprovalStatus
import team.inreok.getiserver.domain.collector.provider.CollectorCollectionContext
import team.inreok.getiserver.domain.collector.provider.CollectorCompanyResolver
import team.inreok.getiserver.domain.collector.provider.CollectorProviderException
import team.inreok.getiserver.domain.collector.provider.NormalizedCollectedJob
import team.inreok.getiserver.domain.collector.provider.ProviderRegistry
import team.inreok.getiserver.domain.collector.repository.CollectionRunErrorRepository
import team.inreok.getiserver.domain.collector.repository.CollectionRunRepository
import team.inreok.getiserver.domain.collector.repository.JobSourceRepository
import team.inreok.getiserver.domain.collector.service.CollectorExecutionService
import team.inreok.getiserver.domain.job.upsert.CollectedJobUpsertCommand
import team.inreok.getiserver.domain.job.upsert.CollectedJobUpsertUseCase
import java.time.LocalDateTime

/**
 * 수집 실행 Orchestrator다. 외부 호출(Provider.collect)과 DB Transaction을 분리하기 위해 이
 * Class 자체에는 `@Transactional`을 붙이지 않는다 — 각 저장 호출은 Spring Data
 * `SimpleJpaRepository`가 제공하는 자체 Transaction으로 짧게 끝난다(docs 17. Transaction
 * Convention). 동일 수집 중복 실행 방지는 ShedLock 같은 분산 Lock 없이, 같은 Source에 이미
 * PENDING/RUNNING CollectionRun이 있는지로 판정한다(API Key도 없고 아직 다중 인스턴스 운영도
 * 아니므로 이번 범위에서는 이 정도로 충분하다고 판단했다. 최종 보고 참고).
 */
@Service
class CollectorExecutionServiceImpl(
    private val jobSourceRepository: JobSourceRepository,
    private val collectionRunRepository: CollectionRunRepository,
    private val collectionRunErrorRepository: CollectionRunErrorRepository,
    private val providerRegistry: ProviderRegistry,
    private val collectedJobUpsertUseCase: CollectedJobUpsertUseCase,
    private val companyResolverProvider: ObjectProvider<CollectorCompanyResolver>,
) : CollectorExecutionService {
    override fun runDailyCollection() {
        val targets =
            jobSourceRepository.findAllByOrderBySourceCodeAsc().filter {
                it.enabled && it.approvalStatus == JobSourceApprovalStatus.READY
            }

        // Provider 하나의 예외가 다른 Provider 실행을 막지 않도록 각 Source를 독립적으로 처리한다.
        targets.forEach { source ->
            runCatching { collectForSource(source) }
                .onFailure { ex -> log.error("Collector 실행 중 처리되지 않은 오류(sourceId={})", source.id, ex) }
        }
    }

    // 이미 진행 중인 실행/등록된 Provider 없음은 오류가 아니라 정상 Skip이라 가드 절로
    // 일찍 반환하는 편이 중첩 if/else보다 읽기 쉽다고 판단해 ReturnCount를 그대로 둔다.
    @Suppress("ReturnCount")
    private fun collectForSource(source: JobSource) {
        val sourceId = requireNotNull(source.id)
        if (collectionRunRepository.existsBySourceIdAndStatusIn(sourceId, ACTIVE_STATUSES)) {
            log.info("Collector 실행 Skip(이미 진행 중): sourceId={}", sourceId)
            return
        }

        val provider = providerRegistry.find(source.sourceCode)
        if (provider == null) {
            // 등록된 Provider가 없는 정상 Skip이다(실패 이력을 만들지 않는다).
            log.info("Collector 실행 Skip(등록된 Provider 없음): sourceCode={}", source.sourceCode)
            return
        }

        val startedAt = LocalDateTime.now()
        var run =
            collectionRunRepository.saveAndFlush(
                CollectionRun(sourceId = sourceId, action = ACTION_SCHEDULED, startedAt = startedAt),
            )
        run.status = CollectionRunStatus.RUNNING
        run = collectionRunRepository.saveAndFlush(run)

        val context = CollectorCollectionContext(requestedAt = startedAt, since = source.lastCollectedAt)
        val result =
            try {
                provider.collect(context)
            } catch (ex: CollectorProviderException) {
                finishAsProviderFailure(run, source, ex)
                return
            }

        var successCount = 0
        var failureCount = 0
        var partialQualityCount = 0

        result.errors.forEach { itemError ->
            recordError(run.id!!, itemError.externalJobId, itemError.code, itemError.message, emptyList())
            failureCount++
        }

        result.jobs.forEach { job ->
            if (job.dataQualityStatus == JobDataQualityStatus.PARTIAL) partialQualityCount++
            val outcome = upsertJob(requireNotNull(run.id), source, job)
            if (outcome) successCount++ else failureCount++
        }

        finishAsCompleted(run, source, successCount, failureCount, partialQualityCount)
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
    ): Boolean {
        val companyId = companyResolverProvider.getIfAvailable()?.resolve(job.companyName)
        if (companyId == null) {
            // Company 공개 계약에 기업명 기준 조회가 없어 해석할 수 없다(임의 기업 생성·고정 ID
            // 금지). 최종 보고의 Blocker로 남긴다.
            recordError(
                runId,
                job.externalJobId,
                CODE_COMPANY_RESOLUTION_NOT_SUPPORTED,
                "기업명(\"${job.companyName}\")으로 companyId를 해석할 수 없어 저장하지 못했습니다.",
                job.missingFields,
            )
            return false
        }

        return try {
            collectedJobUpsertUseCase.upsert(
                CollectedJobUpsertCommand(
                    companyId = companyId,
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
            true
        } catch (ex: RuntimeException) {
            recordError(runId, job.externalJobId, CODE_UPSERT_FAILED, "공고 저장에 실패했습니다.", job.missingFields)
            log.warn("수집 공고 Job 반영 실패: sourceCode={}, externalJobId={}", source.sourceCode, job.externalJobId, ex)
            false
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
        ex: CollectorProviderException,
    ) {
        recordError(
            requireNotNull(run.id),
            externalJobId = null,
            code = ex.code,
            message = ex.message ?: ex.code,
            emptyList(),
        )
        run.status = CollectionRunStatus.FAILED
        run.failureCount = 1
        run.totalCount = 1
        run.finishedAt = LocalDateTime.now()
        collectionRunRepository.saveAndFlush(run)

        source.lastCollectedAt = run.finishedAt
        source.lastFailureAt = run.finishedAt
        source.lastError = ex.message
        jobSourceRepository.saveAndFlush(source)
    }

    private fun finishAsCompleted(
        run: CollectionRun,
        source: JobSource,
        successCount: Int,
        failureCount: Int,
        partialQualityCount: Int,
    ) {
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
        collectionRunRepository.saveAndFlush(run)

        source.lastCollectedAt = run.finishedAt
        if (run.status != CollectionRunStatus.FAILED) source.lastSuccessAt = run.finishedAt
        if (run.status != CollectionRunStatus.SUCCESS) {
            source.lastFailureAt = run.finishedAt
            source.lastError = "일부 또는 전체 공고 처리에 실패했습니다(failureCount=$failureCount)."
        }
        jobSourceRepository.saveAndFlush(source)
    }

    private companion object {
        val ACTIVE_STATUSES = listOf(CollectionRunStatus.PENDING, CollectionRunStatus.RUNNING)

        // CollectorAction 값이 확정되지 않아 실제 계약 값이 아니라 내부 구분용 문자열이다.
        const val ACTION_SCHEDULED = "SCHEDULED_SYNC"
        const val CODE_COMPANY_RESOLUTION_NOT_SUPPORTED = "COMPANY_RESOLUTION_NOT_SUPPORTED"
        const val CODE_UPSERT_FAILED = "COLLECTED_JOB_UPSERT_FAILED"
        private val log = LoggerFactory.getLogger(CollectorExecutionServiceImpl::class.java)
    }
}
