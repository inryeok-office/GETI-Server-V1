package team.inreok.getiserver.domain.notification.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "푸시 알림 설정")
data class NotificationSettingResponse(
    @param:Schema(
        description = "푸시 알림 수신 여부. NotificationType별로 나뉘지 않는 회원당 값 하나이며, 설정한 적 없으면 기본값(true)이다.",
        example = "true",
    )
    val pushEnabled: Boolean,
)
