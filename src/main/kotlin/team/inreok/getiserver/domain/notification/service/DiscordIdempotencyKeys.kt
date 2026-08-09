package team.inreok.getiserver.domain.notification.service

import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryAction
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryTargetType
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 같은 논리적 Discord 작업에 항상 같은 Key를 만든다(후속 요구사항 문서 §20·§49).
 *
 * ## 왜 §20의 예시(`discord:{deliveryId}:{action}:{revision}`)를 쓰지 않는가
 *
 * §49는 이 Key의 UNIQUE 제약으로 **Row 생성 자체**를 막으라고 한다. 그런데 `deliveryId`는 Row를
 * 저장해야 생기는 값이라 Row 생성을 막는 데 쓸 수 없다. 그래서 Key를 Event에서 유도한다.
 *
 * ## 왜 UPDATE만 다른가
 *
 * CREATE/CLOSE_NOTICE/DELETE_NOTICE는 한 대상에 대해 생애주기 중 한 번만 일어난다. Key에
 * 구분자를 두지 않으면 **대상당 영구히 최대 1건**이 보장되어, Discord에서 수동 삭제된 메시지를
 * 몰래 다시 만드는 일(§23에서 금지)이 구조적으로 불가능해진다.
 *
 * UPDATE만 반복될 수 있어 원본이 언제 수정된 버전인지로 구분한다. 같은 Event가 중복 도착하면
 * `updatedAt`이 같아 자동으로 dedup되고, 실제로 다시 수정됐다면 새 Row가 생긴다. 기존 건수를
 * 세어 번호를 매기는 방식과 달리 **조회가 없어 동시 삽입 경합이 생기지 않는다.**
 *
 * 재시도는 이 Key를 다시 만들지 않고 Row에 저장된 값을 그대로 쓰므로, 재시도 중 Key가 바뀌지
 * 않는다는 §20의 핵심 요구가 지켜진다.
 */
object DiscordIdempotencyKeys {
    fun of(
        targetType: DiscordDeliveryTargetType,
        targetId: Long,
        action: DiscordDeliveryAction,
        sourceUpdatedAt: LocalDateTime?,
    ): String {
        val base = "$targetType:$targetId:$action"
        if (action != DiscordDeliveryAction.UPDATE) return base
        // 원본 수정 시각을 모르면(Event가 값을 담지 않은 경우) 매번 다른 Key가 되어 중복 방지가
        // 무력해진다. 그래서 값이 없으면 예외로 드러낸다 -- 조용히 통과시키면 같은 수정 알림이
        // 여러 번 발송된다.
        val revision =
            requireNotNull(sourceUpdatedAt) {
                "UPDATE Delivery는 원본 수정 시각(sourceUpdatedAt)이 있어야 Idempotency Key를 만들 수 있습니다."
            }
        return "$base:${revision.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()}"
    }
}
