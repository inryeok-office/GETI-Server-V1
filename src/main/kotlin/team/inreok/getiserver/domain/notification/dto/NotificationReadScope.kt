package team.inreok.getiserver.domain.notification.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 읽음 처리 대상 범위다(Issue #187, Notion 알림 읽음 처리 API).
 *
 * `JobApplicationAdminAction`과 같이 Request Body의 Discriminator 역할만 하고 저장되지 않으므로
 * `entity/type`이 아니라 `dto`에 둔다.
 */
@Schema(description = "읽음 처리 대상 범위")
enum class NotificationReadScope {
    /** 알림 한 건. `notificationId`가 반드시 필요하다. */
    SINGLE,

    /** 요청자 본인의 읽지 않은 알림 전체. `notificationId`는 전달해도 무시된다. */
    ALL,
}
