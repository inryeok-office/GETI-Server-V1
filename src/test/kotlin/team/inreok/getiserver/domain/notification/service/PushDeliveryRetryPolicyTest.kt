package team.inreok.getiserver.domain.notification.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import team.inreok.getiserver.domain.notification.config.PushDeliveryProperties
import java.time.LocalDateTime

class PushDeliveryRetryPolicyTest {
    private val now = LocalDateTime.of(2026, 8, 24, 10, 0)
    private val policy = PushDeliveryRetryPolicy(PushDeliveryProperties())

    @Test
    fun `첫 실패는 30초 뒤에 다시 시도한다`() {
        assertThat(policy.nextRetryAt(retryCount = 0, now = now)).isEqualTo(now.plusSeconds(30))
    }

    @Test
    fun `두 번째 실패는 2분 뒤에 다시 시도한다`() {
        assertThat(policy.nextRetryAt(retryCount = 1, now = now)).isEqualTo(now.plusMinutes(2))
    }

    @Test
    fun `세 번째 실패는 10분 뒤에 다시 시도한다`() {
        assertThat(policy.nextRetryAt(retryCount = 2, now = now)).isEqualTo(now.plusMinutes(10))
    }

    @Test
    fun `기본 최대 3회를 모두 쓰면 더 이상 예약하지 않는다`() {
        assertThat(policy.nextRetryAt(retryCount = 3, now = now)).isNull()
    }

    @Test
    fun `설정으로 최대 재시도 횟수를 바꿀 수 있다`() {
        val relaxed = PushDeliveryRetryPolicy(PushDeliveryProperties(maxRetryCount = 5))

        // 백오프 목록(30초/2분/10분)보다 상한이 커도 마지막 간격을 재사용해 안전하게 동작한다.
        assertThat(relaxed.nextRetryAt(retryCount = 4, now = now)).isEqualTo(now.plusMinutes(10))
    }
}
