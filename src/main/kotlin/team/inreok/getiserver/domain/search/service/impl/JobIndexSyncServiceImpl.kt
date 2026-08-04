package team.inreok.getiserver.domain.search.service.impl

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.search.config.SearchProperties
import team.inreok.getiserver.domain.search.entity.SearchIndexFailure
import team.inreok.getiserver.domain.search.entity.type.SearchIndexOperation
import team.inreok.getiserver.domain.search.index.JobSearchIndexManager
import team.inreok.getiserver.domain.search.repository.SearchIndexFailureRepository
import team.inreok.getiserver.domain.search.service.JobIndexDocumentBuilder
import team.inreok.getiserver.domain.search.service.JobIndexSyncService
import java.time.LocalDateTime

/**
 * [JobIndexSyncService]의 구현이다(Issue #69). Elasticsearch 호출은 DB Transaction 밖에서
 * 이뤄지고, 실패해도 원본(Postgres) Transaction을 Rollback하지 않는다 — 이 Class 자체가 Job
 * 저장 Transaction과 별도로(Event Listener나 Scheduler에서) 호출되기 때문이다.
 */
@Service
class JobIndexSyncServiceImpl(
    private val documentBuilder: JobIndexDocumentBuilder,
    private val indexManager: JobSearchIndexManager,
    private val failureRepository: SearchIndexFailureRepository,
    private val properties: SearchProperties,
) : JobIndexSyncService {
    private val log = LoggerFactory.getLogger(JobIndexSyncServiceImpl::class.java)

    override fun syncOne(jobId: Long) {
        val document = documentBuilder.buildFor(jobId)
        val operation = if (document != null) SearchIndexOperation.UPSERT else SearchIndexOperation.DELETE
        runCatching {
            if (document != null) indexManager.upsert(document) else indexManager.delete(jobId)
        }.onSuccess {
            clearFailure(jobId)
        }.onFailure { ex ->
            log.warn("공고 색인 동기화 실패(jobId={}, operation={}): {}", jobId, operation, ex.message)
            recordFailure(jobId, operation, ex)
        }
    }

    @Transactional
    fun clearFailure(jobId: Long) {
        failureRepository.deleteByJobId(jobId)
    }

    @Transactional
    fun recordFailure(
        jobId: Long,
        operation: SearchIndexOperation,
        ex: Throwable,
    ) {
        val failure = failureRepository.findByJobId(jobId) ?: SearchIndexFailure(jobId = jobId, operation = operation)
        failure.operation = operation
        failure.attemptCount += 1
        // Elasticsearch 예외 Stack Trace나 내부 정보(Host/Index 이름 등)를 그대로 저장하지 않고
        // 메시지만 남긴다(민감정보 미노출).
        failure.lastError = ex.message?.take(MAX_ERROR_LENGTH)
        failure.nextRetryAt =
            if (failure.attemptCount >= properties.indexRetryMaxAttempts) {
                null
            } else {
                LocalDateTime.now().plusSeconds(backoffSeconds(failure.attemptCount))
            }
        failureRepository.save(failure)
    }

    // 지수 Backoff: attempt 1회차는 기본 대기, 이후 시도마다 2배씩 늘린다.
    private fun backoffSeconds(attemptCount: Int): Long =
        properties.indexRetryBackoffSeconds shl (attemptCount - 1).coerceAtMost(MAX_SHIFT)

    companion object {
        private const val MAX_ERROR_LENGTH = 1000
        private const val MAX_SHIFT = 10
    }
}
