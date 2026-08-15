package team.inreok.getiserver.domain.ai.event

/**
 * `job_ai_analyses` Row가 PENDING으로 (다시) 준비됐으니 실제 OpenAI 호출을 비동기로 시작하라는
 * 내부 신호다. `ai` Module 안에서만 발행/구독하므로 `JobChangedEvent`와 달리 Named Interface로
 * 공개하지 않는다.
 */
data class AiAnalysisRequestedEvent(
    val jobId: Long,
)
