package team.inreok.getiserver.domain.search.event

import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.core.task.TaskExecutor
import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.ai.event.AiAnalysisCompletedEvent
import team.inreok.getiserver.domain.search.service.JobIndexSyncService

/**
 * `ai`가 발행하는 [AiAnalysisCompletedEvent]를 받아 해당 공고 1건만 증분 재색인한다(Issue #144).
 * 전체 Reindex를 트리거하지 않는다 -- 공고 1건의 AI 분석 결과가 바뀐 것뿐이므로
 * `JobIndexSyncService.syncOne`이면 충분하고, 실패하면 기존 `search_index_failures`/
 * `SearchIndexRetryScheduler` 재시도 Infra를 그대로 탄다(AI 전용 재시도 체계를 새로 만들지 않음).
 *
 * `@TransactionalEventListener(AFTER_COMMIT)`가 아니라 일반 [EventListener]를 쓴다 --
 * `AiAnalysisServiceImpl`이 `AiAnalysisTransitionService`(별도 Bean, 자체 `@Transactional`)의
 * Commit이 끝난 *뒤에* 이 Event를 발행하므로, 발행 시점에는 활성 Transaction이 없다
 * (`AiAnalysisWorkEventListener`/`AiAnalysisCompletedEvent` KDoc과 같은 이유).
 */
@Component
class AiAnalysisIndexSyncEventListener(
    private val jobIndexSyncService: JobIndexSyncService,
    private val searchTaskExecutor: TaskExecutor,
) {
    private val log = LoggerFactory.getLogger(AiAnalysisIndexSyncEventListener::class.java)

    @EventListener
    fun onAiAnalysisCompleted(event: AiAnalysisCompletedEvent) {
        searchTaskExecutor.execute {
            runCatching { jobIndexSyncService.syncOne(event.jobId) }
                .onFailure { ex -> log.error("AI 분석 색인 동기화 Event 처리 중 처리되지 않은 오류(jobId={})", event.jobId, ex) }
        }
    }
}
