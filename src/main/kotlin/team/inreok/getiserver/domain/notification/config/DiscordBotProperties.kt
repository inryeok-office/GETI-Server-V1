package team.inreok.getiserver.domain.notification.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * GETI-Bot-V1 Internal API 호출 설정이다(application.yaml의 `app.discord.bot.*`).
 *
 * `domain.collector`의 `app.discord.job-notification.*`(수집 공고용 Discord **Webhook**)과는
 * 완전히 별개의 설정이며 **Secret도 서로 다른 값**이다. 두 값을 같다고 가정하면 안 된다.
 *
 * [baseUrl]과 [internalApiKey]에 기본값을 주지 않는다 -- 안전하지 않은 기본값(`change-me` 등)을
 * 두지 않는다는 규칙(.claude/rules/spring-boot.md)에 따른 것이다. 설정하지 않아도 애플리케이션
 * 기동과 원본 API(공고/프로그램/문의 등록)는 정상 동작하고, [isConfigured]가 false인 동안
 * Worker가 즉시 반환해 Delivery만 PENDING으로 쌓인다(Fail-Fast 아님, Collector와 동일한 방식).
 *
 * Bot 쪽 환경변수 이름은 `GETI_INTERNAL_API_KEY`다. 값은 같고 이름은 각 서비스 관점에서 달라도
 * 된다(후속 요구사항 문서 §6).
 */
@ConfigurationProperties(prefix = "app.discord.bot")
data class DiscordBotProperties(
    val enabled: Boolean = false,
    val baseUrl: String = "",
    val internalApiKey: String = "",
    val connectTimeoutMs: Long = DEFAULT_CONNECT_TIMEOUT_MS,
    /**
     * Bot은 자체적으로 10초 Command Timeout을 걸고 초과 시 `DISCORD_UNAVAILABLE`
     * (`retryable=true`)로 응답한다. 그보다 **길게** 잡아야 Bot이 스스로 판단한 `retryable`을
     * 받아 쓸 수 있다. 짧게 잡으면 Bot이 실제로는 Discord에 게시했는데 서버만 실패로 처리하는
     * 구간이 생긴다.
     */
    val readTimeoutMs: Long = DEFAULT_READ_TIMEOUT_MS,
    /** 자동 재시도 최소 간격이 1분이라(§17) 그보다 촘촘하게 돌 이유가 없다. */
    val sweepIntervalMs: Long = DEFAULT_SWEEP_INTERVAL_MS,
    val staleProcessingThresholdMs: Long = DEFAULT_STALE_PROCESSING_THRESHOLD_MS,
    val maxAutomaticRetryCount: Int = DEFAULT_MAX_AUTOMATIC_RETRY_COUNT,
    val maxManualRetryCount: Int = DEFAULT_MAX_MANUAL_RETRY_COUNT,
    val batchSize: Int = DEFAULT_BATCH_SIZE,
) {
    fun isConfigured(): Boolean = enabled && baseUrl.isNotBlank() && internalApiKey.isNotBlank()

    companion object {
        private const val DEFAULT_CONNECT_TIMEOUT_MS = 2_000L
        private const val DEFAULT_READ_TIMEOUT_MS = 12_000L
        private const val DEFAULT_SWEEP_INTERVAL_MS = 60_000L
        private const val DEFAULT_STALE_PROCESSING_THRESHOLD_MS = 300_000L
        private const val DEFAULT_MAX_AUTOMATIC_RETRY_COUNT = 3
        private const val DEFAULT_MAX_MANUAL_RETRY_COUNT = 3
        private const val DEFAULT_BATCH_SIZE = 50
    }
}
