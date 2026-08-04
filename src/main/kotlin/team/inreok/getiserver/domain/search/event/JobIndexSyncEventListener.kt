package team.inreok.getiserver.domain.search.event

import org.slf4j.LoggerFactory
import org.springframework.core.task.TaskExecutor
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import team.inreok.getiserver.domain.job.event.JobChangedEvent
import team.inreok.getiserver.domain.search.service.JobIndexSyncService

/**
 * Job이 발행하는 [JobChangedEvent]를 받아 색인을 동기화한다(Issue #69). Commit 이후에만
 * 실행되어(`AFTER_COMMIT`) 아직 반영되지 않은 값을 색인하지 않는다. Elasticsearch 호출은
 * 요청·저장 Thread를 막지 않도록 전용 `searchTaskExecutor`로 넘긴다 — 색인이 느리거나 실패해도
 * 원래 요청(공고 등록/수정 등)의 응답 시간에 영향을 주지 않는다.
 */
@Component
class JobIndexSyncEventListener(
    private val jobIndexSyncService: JobIndexSyncService,
    private val searchTaskExecutor: TaskExecutor,
) {
    private val log = LoggerFactory.getLogger(JobIndexSyncEventListener::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onJobChanged(event: JobChangedEvent) {
        searchTaskExecutor.execute {
            runCatching { jobIndexSyncService.syncOne(event.jobId) }
                .onFailure { ex -> log.error("색인 동기화 Event 처리 중 처리되지 않은 오류(jobId={})", event.jobId, ex) }
        }
    }
}
