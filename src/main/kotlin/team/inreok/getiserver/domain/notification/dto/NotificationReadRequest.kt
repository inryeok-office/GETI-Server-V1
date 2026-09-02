package team.inreok.getiserver.domain.notification.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 읽음 처리 요청이다. [scope]에 따라 필요한 필드가 달라지는 구조는
 * `JobApplicationAdminActionRequest`와 같다 -- 무관한 필드는 무시하고, 필수인데 비어 있으면
 * Service가 전용 예외로 400을 돌려준다.
 */
@Schema(description = "알림 읽음 처리 요청")
data class NotificationReadRequest(
    @param:Schema(description = "읽음 처리 대상 범위", example = "SINGLE")
    val scope: NotificationReadScope,
    @param:Schema(
        description =
            "읽음 처리할 알림 ID. scope=SINGLE이면 필수이고, 없으면 400(NOTIFICATION_ID_REQUIRED)이다. " +
                "scope=ALL에서는 전달해도 무시된다.",
        example = "1",
        nullable = true,
    )
    val notificationId: Long? = null,
)
