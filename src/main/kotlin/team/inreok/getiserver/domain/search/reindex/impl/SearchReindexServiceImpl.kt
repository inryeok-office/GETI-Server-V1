package team.inreok.getiserver.domain.search.reindex.impl

import org.slf4j.LoggerFactory
import org.springframework.core.task.TaskExecutor
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import team.inreok.getiserver.domain.ai.query.AiAnalysisSearchQueryPort
import team.inreok.getiserver.domain.job.query.JobIndexQueryPort
import team.inreok.getiserver.domain.search.config.SearchProperties
import team.inreok.getiserver.domain.search.entity.SearchReindexRun
import team.inreok.getiserver.domain.search.entity.type.SearchReindexStatus
import team.inreok.getiserver.domain.search.exception.ReindexAlreadyRunningException
import team.inreok.getiserver.domain.search.index.JobSearchIndexManager
import team.inreok.getiserver.domain.search.reindex.SearchReindexService
import team.inreok.getiserver.domain.search.repository.SearchReindexRunRepository
import team.inreok.getiserver.domain.search.service.JobIndexDocumentBuilder
import java.time.LocalDateTime

/**
 * [SearchReindexService]의 구현이다(Issue #69). `CollectorExecutionServiceImpl`과 같은 이유로
 * Class 전체에는 `@Transactional`을 붙이지 않는다 — 각 저장 호출은 Spring Data
 * `SimpleJpaRepository`가 제공하는 자체 Transaction으로 짧게 끝나고, 외부 호출(Elasticsearch)을
 * 그 안에 두지 않는다. Kotlin private 메서드는 같은 Class 안에서 호출하면 Spring AOP Proxy를
 * 거치지 않아 `@Transactional`을 붙여도 실제로 적용되지 않으므로 애초에 붙이지 않는다.
 */
@Service
class SearchReindexServiceImpl(
    private val reindexRunRepository: SearchReindexRunRepository,
    private val jobIndexQueryPort: JobIndexQueryPort,
    private val documentBuilder: JobIndexDocumentBuilder,
    private val aiAnalysisSearchQueryPort: AiAnalysisSearchQueryPort,
    private val indexManager: JobSearchIndexManager,
    private val searchTaskExecutor: TaskExecutor,
    private val properties: SearchProperties,
) : SearchReindexService {
    private val log = LoggerFactory.getLogger(SearchReindexServiceImpl::class.java)

    override fun triggerReindex(): SearchReindexRun {
        if (reindexRunRepository.existsByStatusIn(ACTIVE_STATUSES)) throw ReindexAlreadyRunningException()

        // 위 existsByStatusIn 확인과 이 INSERT 사이에는 DB 잠금이 없어(TOCTOU), 거의 동시에 들어온
        // 두 요청이 모두 확인을 통과할 수 있다. uk_search_reindex_runs_active_singleton(활성 Row
        // 최대 1건 Partial Unique Index, V10 Migration)이 최종 방어선이다 — 위반하면 DB가
        // DataIntegrityViolationException을 던지고 여기서 REINDEX_ALREADY_RUNNING(409)으로
        // 변환한다(PR #70 Review 반영).
        val run =
            try {
                reindexRunRepository.saveAndFlush(SearchReindexRun())
            } catch (ex: DataIntegrityViolationException) {
                // BusinessException은 cause를 받지 않아 원본 예외를 여기서 남긴다(detekt
                // SwallowedException 반영) — 실제 제약 위반 여부를 나중에 DB 로그로도 추적할 수 있다.
                log.warn("재색인 동시 요청이 DB 제약으로 차단됨(uk_search_reindex_runs_active_singleton)", ex)
                throw ReindexAlreadyRunningException()
            }
        val runId = requireNotNull(run.id)
        // 요청 Thread는 여기서 반환한다(202 Accepted) — 실제 재색인은 별도 Thread에서 진행한다.
        searchTaskExecutor.execute {
            runCatching { execute(runId) }
                .onFailure { ex -> log.error("전체 재색인 실행 중 처리되지 않은 오류(runId={})", runId, ex) }
        }
        return run
    }

    private fun execute(runId: Long) {
        markRunning(runId)

        val newIndexName = indexManager.createNewPhysicalIndex()
        var total = 0L
        var success = 0L
        var failure = 0L

        // runCatching으로 감싸 예외 종류와 무관하게(Elasticsearch/Postgres 장애 등) 실패를
        // 하나의 경로로 처리한다. 도중에 실패해도 그때까지 누적된 건수(total/success/failure)를
        // 그대로 실패 이력에 남긴다.
        runCatching {
            var afterId = 0L
            while (true) {
                val snapshots = jobIndexQueryPort.findForReindex(afterId, properties.reindexBatchSize)
                if (snapshots.isEmpty()) break

                // 이번 Page에 담긴 공고들의 AI 분석 결과를 한 번에 조회한다 -- 공고 건수만큼
                // 반복 조회하면 N+1이 된다(Issue #144 요구사항).
                val aiSnapshots = aiAnalysisSearchQueryPort.findCompletedByJobIds(snapshots.map { it.jobId })
                val documents =
                    snapshots.mapNotNull { snapshot ->
                        runCatching { documentBuilder.build(snapshot, aiSnapshots[snapshot.jobId]) }
                            .onFailure { ex -> log.warn("재색인 중 공고 1건 Document 생성 실패(jobId={})", snapshot.jobId, ex) }
                            .getOrNull()
                    }
                indexManager.bulkIndex(newIndexName, documents)

                total += snapshots.size
                success += documents.size
                failure += snapshots.size - documents.size
                afterId = snapshots.last().jobId
            }

            // 새 Index를 완전히 채운 뒤에만 Alias를 전환한다 — 실패하면 기존 Index/Alias가 그대로
            // 유지되어 검색 공백이 생기지 않는다.
            val previousIndices = indexManager.resolveIndicesBehindAlias()
            indexManager.switchAlias(newIndexName, previousIndices)
            previousIndices.forEach(indexManager::deleteIndex)
        }.onSuccess {
            markCompleted(runId, total, success, failure)
        }.onFailure { ex ->
            log.error("전체 재색인 실패(runId={}, newIndex={})", runId, newIndexName, ex)
            // 아직 Alias에 연결하지 않은 새 Index만 정리한다 — 기존 검색 Index는 손대지 않는다.
            indexManager.deleteIndex(newIndexName)
            markFailed(runId, ex, total, success, failure)
        }
    }

    private fun markRunning(runId: Long) {
        val run = reindexRunRepository.findById(runId).orElseThrow()
        run.status = SearchReindexStatus.RUNNING
        run.startedAt = LocalDateTime.now()
        reindexRunRepository.save(run)
    }

    private fun markCompleted(
        runId: Long,
        total: Long,
        success: Long,
        failure: Long,
    ) {
        val run = reindexRunRepository.findById(runId).orElseThrow()
        run.status = if (failure == 0L) SearchReindexStatus.SUCCESS else SearchReindexStatus.PARTIAL_SUCCESS
        run.totalCount = total
        run.successCount = success
        run.failureCount = failure
        run.completedAt = LocalDateTime.now()
        reindexRunRepository.save(run)
    }

    private fun markFailed(
        runId: Long,
        ex: Throwable,
        total: Long,
        success: Long,
        failure: Long,
    ) {
        val run = reindexRunRepository.findById(runId).orElseThrow()
        run.status = SearchReindexStatus.FAILED
        run.totalCount = total
        run.successCount = success
        run.failureCount = failure
        // 예외 Stack Trace나 Elasticsearch 내부 정보(Host/Index 등)를 그대로 저장하지 않는다.
        run.errorMessage = ex.message?.take(MAX_ERROR_LENGTH)
        run.completedAt = LocalDateTime.now()
        reindexRunRepository.save(run)
    }

    companion object {
        private val ACTIVE_STATUSES = listOf(SearchReindexStatus.PENDING, SearchReindexStatus.RUNNING)
        private const val MAX_ERROR_LENGTH = 1000
    }
}
