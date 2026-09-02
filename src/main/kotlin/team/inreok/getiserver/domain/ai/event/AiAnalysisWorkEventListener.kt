package team.inreok.getiserver.domain.ai.event

import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.core.task.TaskExecutor
import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.ai.service.AiAnalysisService

/**
 * [AiAnalysisRequestedEvent]를 받아 실제 OpenAI 호출(`AiAnalysisService.processAnalysis`)을
 * 시작한다.
 *
 * `@TransactionalEventListener(AFTER_COMMIT)`가 아니라 일반 [EventListener]를 쓴다 -- 이 Event는
 * 항상 `AiAnalysisTransitionService`(별도 Bean, 자체 `@Transactional`)가 이미 Commit을 마친
 * *뒤에* `AiAnalysisServiceImpl`이 발행한다(Self-Invocation 회피 설계, `AiAnalysisTransitionService`
 * KDoc 참고). 즉 발행 시점에는 활성 Transaction이 없으므로 `@TransactionalEventListener`를 쓰면
 * "Commit 이후 실행"을 걸어 줄 Transaction 자체가 없어 Event가 조용히 버려진다(실제 Live E2E
 * 검증에서 발견, Issue #132) -- PENDING으로 멈춘 채 Worker가 전혀 실행되지 않는 증상으로
 * 나타났다. `JobPublishedAiTriggerListener`(`JobChangedEvent` 구독)는 `JobServiceImpl`의
 * `@Transactional` Method 안에서 발행되므로 그 반대로 `@TransactionalEventListener`가 맞다 --
 * 두 Listener의 Annotation이 다른 것은 실수가 아니라 발행 시점의 Transaction 유무 차이다.
 */
@Component
class AiAnalysisWorkEventListener(
    private val aiAnalysisService: AiAnalysisService,
    private val aiTaskExecutor: TaskExecutor,
) {
    private val log = LoggerFactory.getLogger(AiAnalysisWorkEventListener::class.java)

    @EventListener
    fun onAiAnalysisRequested(event: AiAnalysisRequestedEvent) {
        aiTaskExecutor.execute {
            runCatching { aiAnalysisService.processAnalysis(event.jobId) }
                .onFailure { ex -> log.error("AI 분석 Worker 처리 중 처리되지 않은 오류(jobId={})", event.jobId, ex) }
        }
    }
}
