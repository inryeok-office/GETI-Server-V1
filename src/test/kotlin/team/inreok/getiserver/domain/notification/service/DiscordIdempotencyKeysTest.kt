package team.inreok.getiserver.domain.notification.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryAction
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryTargetType
import java.time.LocalDateTime
import java.time.ZoneId

class DiscordIdempotencyKeysTest {
    private val updatedAt = LocalDateTime.of(2026, 8, 9, 10, 0)

    @Test
    fun `CREATE는 대상당 하나의 Key만 만든다`() {
        val first = key(DiscordDeliveryAction.CREATE)
        val second = key(DiscordDeliveryAction.CREATE)

        assertThat(first).isEqualTo("PROGRAM:123:CREATE")
        assertThat(second).isEqualTo(first)
    }

    @Test
    fun `CLOSE_NOTICE와 DELETE_NOTICE도 대상당 하나의 Key만 만든다`() {
        assertThat(key(DiscordDeliveryAction.CLOSE_NOTICE)).isEqualTo("PROGRAM:123:CLOSE_NOTICE")
        assertThat(key(DiscordDeliveryAction.DELETE_NOTICE)).isEqualTo("PROGRAM:123:DELETE_NOTICE")
    }

    @Test
    fun `같은 Event가 중복 도착해도 UPDATE Key가 같다`() {
        val first = key(DiscordDeliveryAction.UPDATE, updatedAt)
        val second = key(DiscordDeliveryAction.UPDATE, updatedAt)

        assertThat(second).isEqualTo(first)
    }

    @Test
    fun `원본이 다시 수정되면 UPDATE Key가 달라진다`() {
        val first = key(DiscordDeliveryAction.UPDATE, updatedAt)
        val second = key(DiscordDeliveryAction.UPDATE, updatedAt.plusMinutes(1))

        assertThat(second).isNotEqualTo(first)
    }

    @Test
    fun `UPDATE Key는 원본 수정 시각을 epochMilli로 담는다`() {
        val expected = updatedAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        assertThat(key(DiscordDeliveryAction.UPDATE, updatedAt)).isEqualTo("PROGRAM:123:UPDATE:$expected")
    }

    @Test
    fun `원본 수정 시각 없이 UPDATE Key를 만들면 예외가 발생한다`() {
        // 조용히 통과시키면 매번 다른 Key가 되어 같은 수정 알림이 여러 번 발송된다.
        assertThatThrownBy { key(DiscordDeliveryAction.UPDATE, sourceUpdatedAt = null) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `대상 종류가 다르면 Key가 달라진다`() {
        val program = key(DiscordDeliveryAction.CREATE, targetType = DiscordDeliveryTargetType.PROGRAM)
        val job = key(DiscordDeliveryAction.CREATE, targetType = DiscordDeliveryTargetType.JOB)

        assertThat(job).isNotEqualTo(program)
    }

    @Test
    fun `Key가 Bot Header 상한인 200자를 넘지 않는다`() {
        val key = key(DiscordDeliveryAction.UPDATE, updatedAt, targetId = Long.MAX_VALUE)

        assertThat(key.length).isLessThanOrEqualTo(200)
    }

    private fun key(
        action: DiscordDeliveryAction,
        sourceUpdatedAt: LocalDateTime? = null,
        targetType: DiscordDeliveryTargetType = DiscordDeliveryTargetType.PROGRAM,
        targetId: Long = 123L,
    ) = DiscordIdempotencyKeys.of(targetType, targetId, action, sourceUpdatedAt)
}
