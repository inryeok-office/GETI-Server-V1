package team.inreok.getiserver.domain.notification.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Push 전달 재시도·Sweep 설정이다(application.yaml의 `app.push.delivery.*`, Issue #190).
 *
 * 기본 [sweepIntervalMs]는 Discord Bot Worker(60초)보다 짧다 -- Discord 자동 재시도 최소
 * 백오프가 1분인 것과 달리 Push는 "사용자에게 실시간에 가깝게 도착해야 하는" 알림이라 더 촘촘히
 * 돈다(첫 백오프 30초, [PushDeliveryRetryPolicy] 참고).
 */
@ConfigurationProperties(prefix = "app.push.delivery")
data class PushDeliveryProperties(
    val sweepIntervalMs: Long = DEFAULT_SWEEP_INTERVAL_MS,
    val staleProcessingThresholdMs: Long = DEFAULT_STALE_PROCESSING_THRESHOLD_MS,
    /** 자동 재시도 최대 횟수다(Issue #190 확정 계약: 기본 최대 3회). */
    val maxRetryCount: Int = DEFAULT_MAX_RETRY_COUNT,
    val batchSize: Int = DEFAULT_BATCH_SIZE,
) {
    companion object {
        private const val DEFAULT_SWEEP_INTERVAL_MS = 15_000L
        private const val DEFAULT_STALE_PROCESSING_THRESHOLD_MS = 120_000L
        private const val DEFAULT_MAX_RETRY_COUNT = 3
        private const val DEFAULT_BATCH_SIZE = 100
    }
}
