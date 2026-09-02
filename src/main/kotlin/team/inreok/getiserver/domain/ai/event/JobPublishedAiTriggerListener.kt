package team.inreok.getiserver.domain.ai.event

import org.slf4j.LoggerFactory
import org.springframework.core.task.TaskExecutor
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import team.inreok.getiserver.domain.ai.service.AiAnalysisService
import team.inreok.getiserver.domain.job.event.JobChangedEvent
import team.inreok.getiserver.domain.job.query.JobAiAnalysisInputQueryPort

/**
 * `job`이 발행하는 [JobChangedEvent]를 받아 공고가 처음 PUBLISHED됐다면 AI 분석을 시작한다(GETI
 * Notion AI Analysis Phase 1 "분석 Trigger" 절). `JobIndexSyncEventListener`와 같은 이유로
 * `AFTER_COMMIT`에서만 실행하고, OpenAI 호출로 이어질 수 있는 작업이 요청 Thread를 막지 않도록
 * 전용 `aiTaskExecutor`로 넘긴다 -- Job 게시 API 성공 여부는 AI 분석 성공 여부와 분리된다.
 *
 * `JobChangedEvent`는 `jobId`만 담고 변경 종류를 담지 않으므로(Class 문서 참고), 매 Event마다
 * 현재 Job 상태를 다시 조회해서 PUBLISHED인지 판단한다. `AiAnalysisService.triggerInitialAnalysisIfAbsent`가
 * 이미 분석 Row가 있으면 아무 것도 하지 않으므로, 같은 공고가 PUBLISHED 이후 다시 수정되어
 * Event가 여러 번 와도 최초 1회만 분석을 시작한다.
 */
@Component
class JobPublishedAiTriggerListener(
    private val jobAiAnalysisInputQueryPort: JobAiAnalysisInputQueryPort,
    private val aiAnalysisService: AiAnalysisService,
    private val aiTaskExecutor: TaskExecutor,
) {
    private val log = LoggerFactory.getLogger(JobPublishedAiTriggerListener::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onJobChanged(event: JobChangedEvent) {
        aiTaskExecutor.execute {
            runCatching {
                val snapshot = jobAiAnalysisInputQueryPort.findById(event.jobId)
                if (snapshot?.status == JOB_STATUS_PUBLISHED) {
                    aiAnalysisService.triggerInitialAnalysisIfAbsent(event.jobId)
                }
            }.onFailure { ex -> log.error("AI 분석 Trigger 처리 중 오류(jobId={})", event.jobId, ex) }
        }
    }

    private companion object {
        const val JOB_STATUS_PUBLISHED = "PUBLISHED"
    }
}
