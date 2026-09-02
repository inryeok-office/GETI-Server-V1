package team.inreok.getiserver.domain.notification.dto

import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetType
import team.inreok.getiserver.domain.notification.entity.type.NotificationType

/**
 * 인앱 알림 생성 입력이다. REST로 노출되지 않는 Module 내부 계약이며, Domain Event를 수신해
 * 알림을 만드는 후속 작업이 이 계약을 그대로 사용한다(원본 요구사항 문서 5절 — 외부 Domain이
 * `NotificationService`를 직접 호출하는 방식보다 Event 수신을 우선한다).
 *
 * [sourceEventType]/[sourceEventId]는 중복 알림 방지(같은 문서 6절, Issue #193)에 쓰는 Idempotency
 * Identity의 나머지 두 축이다(나머지 한 축은 [recipientMemberId]). 이 Command를 만드는 모든
 * 호출부(현재는 `domain.notification.event`의 4개 Listener뿐)는 새 알림을 만들 때마다 이 두 값을
 * 항상 채워야 한다 — 원본을 발행한 Domain Event 종류(예: `InquiryAnsweredEvent`)와 그 Event의
 * 안정적인 식별자다. DB의 `source_event_type`/`source_event_id` Column은 과거(이 Command가 두
 * 값을 요구하기 전) Row와의 호환을 위해 nullable로 남아 있지만, 이 Command는 non-null 필수
 * Parameter로 받아 **새 생성은 항상 두 값을 채우도록 Service 계층(Kotlin Type System)에서
 * 강제**한다(DB 제약이 아니라 여기서 강제하는 이유는 [team.inreok.getiserver.domain.notification.entity.Notification]
 * KDoc 참고).
 *
 * 같은 [recipientMemberId] + [sourceEventType] + [sourceEventId] 조합으로 다시 생성을 시도하면
 * [team.inreok.getiserver.domain.notification.service.NotificationService.create]가 조용히
 * 기존 알림을 재사용한다(DB UNIQUE 제약, `NotificationServiceImpl` 참고).
 */
data class NotificationCreateCommand(
    val recipientMemberId: Long,
    val type: NotificationType,
    val title: String,
    val content: String,
    val sourceEventType: String,
    val sourceEventId: Long,
    val targetType: NotificationTargetType? = null,
    val targetId: Long? = null,
)
