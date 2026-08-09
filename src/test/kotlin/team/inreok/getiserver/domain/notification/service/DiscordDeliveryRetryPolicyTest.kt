package team.inreok.getiserver.domain.notification.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import team.inreok.getiserver.domain.notification.config.DiscordBotProperties
import java.time.Duration
import java.time.LocalDateTime

class DiscordDeliveryRetryPolicyTest {
    private val now = LocalDateTime.of(2026, 8, 9, 10, 0)
    private val policy = DiscordDeliveryRetryPolicy(DiscordBotProperties())

    @Test
    fun `첫 실패는 1분 뒤에 다시 시도한다`() {
        assertThat(policy.nextRetryAt(automaticRetryCount = 0, now = now)).isEqualTo(now.plusMinutes(1))
    }

    @Test
    fun `두 번째 실패는 5분 뒤에 다시 시도한다`() {
        assertThat(policy.nextRetryAt(automaticRetryCount = 1, now = now)).isEqualTo(now.plusMinutes(5))
    }

    @Test
    fun `세 번째 실패는 30분 뒤에 다시 시도한다`() {
        assertThat(policy.nextRetryAt(automaticRetryCount = 2, now = now)).isEqualTo(now.plusMinutes(30))
    }

    @Test
    fun `자동 재시도 3회를 모두 쓰면 더 이상 예약하지 않는다`() {
        assertThat(policy.nextRetryAt(automaticRetryCount = 3, now = now)).isNull()
    }

    @Test
    fun `Bot이 알려준 대기 시간이 있으면 기본 백오프보다 우선한다`() {
        val retryAfter = Duration.ofSeconds(90)

        assertThat(policy.nextRetryAt(automaticRetryCount = 0, now = now, retryAfter = retryAfter))
            .isEqualTo(now.plusSeconds(90))
    }

    @Test
    fun `상한을 모두 쓴 뒤에는 Bot이 대기 시간을 줘도 예약하지 않는다`() {
        // 재시도 여부는 서버가 최종 판단한다(요구사항 §16).
        assertThat(policy.nextRetryAt(automaticRetryCount = 3, now = now, retryAfter = Duration.ofSeconds(1)))
            .isNull()
    }

    @Test
    fun `수동 재시도는 상한 미만일 때만 허용한다`() {
        assertThat(policy.canRetryManually(0)).isTrue()
        assertThat(policy.canRetryManually(2)).isTrue()
        assertThat(policy.canRetryManually(3)).isFalse()
    }

    @Test
    fun `설정으로 자동 재시도 상한을 바꿀 수 있다`() {
        val relaxed = DiscordDeliveryRetryPolicy(DiscordBotProperties(maxAutomaticRetryCount = 5))

        // 백오프 목록(1/5/30분)보다 상한이 커도 마지막 간격을 재사용해 안전하게 동작한다.
        assertThat(relaxed.nextRetryAt(automaticRetryCount = 4, now = now)).isEqualTo(now.plusMinutes(30))
    }
}
