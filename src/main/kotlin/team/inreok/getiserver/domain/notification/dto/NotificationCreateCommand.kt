package team.inreok.getiserver.domain.notification.dto

import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetType
import team.inreok.getiserver.domain.notification.entity.type.NotificationType

/**
 * 인앱 알림 생성 입력이다. REST로 노출되지 않는 Module 내부 계약이며, Domain Event를 수신해
 * 알림을 만드는 후속 작업이 이 계약을 그대로 사용한다(원본 요구사항 문서 5절 — 외부 Domain이
 * `NotificationService`를 직접 호출하는 방식보다 Event 수신을 우선한다).
 *
 * 중복 알림 방지에 필요한 `idempotencyKey`/`sourceEventType`/`sourceEventId`(같은 문서 6절)는
 * 아직 포함하지 않는다 — Unique 제약의 범위가 "누구에게 어떤 이벤트를 알릴지"(25절 8·9번, 미확정)에
 * 따라 달라져서, 지금 정하면 추측이 된다. Event를 실제로 연결하는 PR에서 Column·제약과 함께 추가한다.
 */
data class NotificationCreateCommand(
    val recipientMemberId: Long,
    val type: NotificationType,
    val title: String,
    val content: String,
    val targetType: NotificationTargetType? = null,
    val targetId: Long? = null,
)
