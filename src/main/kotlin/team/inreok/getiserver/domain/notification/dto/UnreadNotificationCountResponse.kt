package team.inreok.getiserver.domain.notification.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "읽지 않은 알림 개수")
data class UnreadNotificationCountResponse(
    @param:Schema(description = "읽지 않은 알림 개수", example = "3")
    val unreadCount: Long,
)
