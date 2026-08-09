package team.inreok.getiserver.domain.notification.service

import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.notification.config.DiscordBotProperties
import java.time.Duration
import java.time.LocalDateTime

/**
 * 자동 재시도 간격과 상한을 계산하는 단일 지점이다(후속 요구사항 문서 §17).
 *
 * Bot이 `retryable`을 내려주지만 **최종 Retry Policy의 Source of Truth는 서버**다(§16). 예를
 * 들어 연결 실패나 Timeout은 Bot이 응답조차 못 한 상황이라 서버가 독자적으로 재시도 가능으로
 * 분류한다.
 */
@Component
class DiscordDeliveryRetryPolicy(
    private val properties: DiscordBotProperties,
) {
    /**
     * 다음 자동 재시도 시각이다. 남은 횟수가 없으면 null -- 호출부는 이때 FAILED로 확정한다.
     *
     * @param automaticRetryCount 지금까지 수행한 자동 시도 횟수(이번 실패를 포함하기 전 값).
     * @param retryAfter Bot이 알려준 대기 시간. 현재 Bot은 이 값을 내려주지 않지만, 429
     *   응답에 `Retry-After`가 추가되면 기본 백오프보다 우선한다(§17).
     */
    fun nextRetryAt(
        automaticRetryCount: Int,
        now: LocalDateTime,
        retryAfter: Duration? = null,
    ): LocalDateTime? {
        if (automaticRetryCount >= properties.maxAutomaticRetryCount) return null
        val backoff = retryAfter ?: backoffAt(automaticRetryCount)
        return now.plus(backoff)
    }

    /** 수동 재시도를 더 할 수 있는지다(§18). 상태 조건(FAILED)은 호출부가 확인한다. */
    fun canRetryManually(manualRetryCount: Int): Boolean = manualRetryCount < properties.maxManualRetryCount

    /**
     * 1분 → 5분 → 30분. 실패가 이어질수록 간격을 벌려 Discord Rate Limit과 Bot 장애를 악화시키지
     * 않는다. [BACKOFFS] 범위를 넘는 호출은 [nextRetryAt]이 이미 걸러내지만, 상한을 바꿔도
     * 안전하도록 마지막 값을 재사용한다.
     */
    private fun backoffAt(automaticRetryCount: Int): Duration =
        BACKOFFS.getOrElse(automaticRetryCount) { BACKOFFS.last() }

    private companion object {
        val BACKOFFS =
            listOf(
                Duration.ofMinutes(1),
                Duration.ofMinutes(5),
                Duration.ofMinutes(30),
            )
    }
}
