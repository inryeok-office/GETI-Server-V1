package team.inreok.getiserver.domain.notification.service

import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.notification.config.PushDeliveryProperties
import java.time.Duration
import java.time.LocalDateTime

/**
 * 재시도 간격과 상한을 계산하는 단일 지점이다(`DiscordDeliveryRetryPolicy`와 같은 구조).
 *
 * 백오프 간격(30초 → 2분 → 10분)은 Discord(1분 → 5분 → 30분)보다 짧다 -- Push는 사용자에게
 * 실시간에 가깝게 도착해야 하는 알림이고, FCM은 Discord Bot보다 Rate Limit이 훨씬 여유롭다.
 */
@Component
class PushDeliveryRetryPolicy(
    private val properties: PushDeliveryProperties,
) {
    /**
     * 다음 자동 재시도 시각이다. 남은 횟수가 없으면 null -- 호출부는 이때 FAILED로 확정한다.
     *
     * @param retryCount 지금까지 수행한 시도 횟수(이번 실패를 포함하기 전 값).
     */
    fun nextRetryAt(
        retryCount: Int,
        now: LocalDateTime,
    ): LocalDateTime? {
        if (retryCount >= properties.maxRetryCount) return null
        return now.plus(backoffAt(retryCount))
    }

    private fun backoffAt(retryCount: Int): Duration = BACKOFFS.getOrElse(retryCount) { BACKOFFS.last() }

    private companion object {
        val BACKOFFS =
            listOf(
                Duration.ofSeconds(30),
                Duration.ofMinutes(2),
                Duration.ofMinutes(10),
            )
    }
}
