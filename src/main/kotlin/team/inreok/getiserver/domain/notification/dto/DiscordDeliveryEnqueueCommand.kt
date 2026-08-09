package team.inreok.getiserver.domain.notification.dto

import team.inreok.getiserver.domain.notification.entity.type.DiscordMessageTemplate
import java.time.LocalDateTime

/**
 * Discord 전달을 예약해 달라는 내부 요청이다. **REST로 노출되지 않는다** -- 이 Command를
 * 만드는 것은 Job/Program/Inquiry의 Domain Event Listener이고(후속 PR 범위), 사용자가 직접
 * 채널이나 Role을 지정하는 API는 만들지 않는다(§25).
 *
 * [template]이 `targetType`과 `action`을 이미 들고 있어 세 값을 따로 받지 않는다. 세 값의
 * 조합은 Bot이 `refineTemplateConsistency`로 검증하므로 서버에서 어긋나게 만들 수 없어야 한다.
 *
 * [sourceUpdatedAt]은 UPDATE의 Idempotency Key를 만드는 데만 쓴다(§6.3) -- 원본이 언제 수정된
 * 버전인지 구분하는 값이라, 같은 Event가 중복 도착하면 같은 Key가 나와 Row가 하나만 생긴다.
 * UPDATE가 아닌 Action에는 필요 없다.
 */
data class DiscordDeliveryEnqueueCommand(
    val template: DiscordMessageTemplate,
    val targetId: Long,
    val channelId: String,
    /**
     * Mention할 Discord Role Snowflake 목록이다. CREATE가 아닌 Action에서는 무시된다 --
     * Mention은 최초 성공 CREATE 한 번만 허용되기 때문이다(§24).
     */
    val roleIds: List<String> = emptyList(),
    val sourceUpdatedAt: LocalDateTime? = null,
)
