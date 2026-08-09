package team.inreok.getiserver.domain.notification.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "단일 알림 읽음 처리 결과")
data class NotificationReadResponse(
    @param:Schema(description = "알림 ID", example = "1")
    val notificationId: Long,
    @param:Schema(description = "읽음 여부(처리 후 항상 true)", example = "true")
    val isRead: Boolean,
    @param:Schema(description = "읽은 시각. 이미 읽은 알림이면 처음 읽은 시각이 그대로 유지된다.")
    val readAt: LocalDateTime,
)
