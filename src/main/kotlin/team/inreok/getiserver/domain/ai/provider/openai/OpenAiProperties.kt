package team.inreok.getiserver.domain.ai.provider.openai

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * OpenAI Responses API 호출 설정이다(application.yaml의 `app.ai.openai.*`,
 * `DiscordBotProperties`와 같은 방식). [apiKey]에는 안전하지 않은 기본값(`change-me` 등)을 두지
 * 않는다(.claude/rules/spring-boot.md) -- 비어 있으면 [isConfigured]가 false가 되어
 * `OpenAiAnalysisProvider`가 즉시 [team.inreok.getiserver.domain.ai.provider.AiAnalysisProviderException.NotConfigured]를
 * 던지고, 애플리케이션 기동 자체는 그대로 정상 동작한다(Fail-Fast 아님, Collector/Discord Bot과 동일한 방식).
 *
 * [model]은 실제 사용 가능한 비용 효율 모델을 환경변수(`OPENAI_MODEL`)로 주입한다. Business
 * Code(`OpenAiAnalysisProvider`)에 Model 이름을 Hard Coding하지 않기 위한 설정이며, 기본값은
 * 값이 없을 때도 애플리케이션이 기동하도록 두는 최소한의 Fallback일 뿐 실제 운영 값이 아니다.
 */
@ConfigurationProperties(prefix = "app.ai.openai")
data class OpenAiProperties(
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
    val baseUrl: String = DEFAULT_BASE_URL,
    val connectTimeoutMs: Long = DEFAULT_CONNECT_TIMEOUT_MS,
    /** 분석 1건이 공고 본문 전체를 포함해 Model이 응답을 구성하는 데 시간이 걸릴 수 있어 여유 있게 잡는다. */
    val readTimeoutMs: Long = DEFAULT_READ_TIMEOUT_MS,
) {
    fun isConfigured(): Boolean = apiKey.isNotBlank()

    companion object {
        private const val DEFAULT_MODEL = "gpt-4o-mini"
        private const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        private const val DEFAULT_CONNECT_TIMEOUT_MS = 5_000L
        private const val DEFAULT_READ_TIMEOUT_MS = 60_000L
    }
}
