package team.inreok.getiserver.domain.notification.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "푸시 알림 설정 변경 요청")
data class NotificationSettingUpdateRequest(
    @param:Schema(description = "푸시 알림을 받을지(true) 끌지(false) 여부", example = "true")
    val pushEnabled: Boolean,
)
