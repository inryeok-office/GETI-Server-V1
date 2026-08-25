package team.inreok.getiserver.domain.notification.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "알림 읽음 처리 결과")
data class NotificationReadResponse(
    @param:Schema(description = "처리 후 남은 읽지 않은 알림 개수", example = "4")
    val unreadCount: Long,
    @param:Schema(
        description = "이번 요청으로 실제 상태가 바뀐 알림 개수. 이미 읽은 알림만 대상이었다면 0이다.",
        example = "1",
    )
    val updatedCount: Long,
    @param:Schema(
        description =
            "읽은 시각. scope=SINGLE이면 대상 알림의 읽은 시각(이미 읽은 알림이면 처음 읽은 시각이 " +
                "그대로 유지된다)이고, scope=ALL이면 이번 요청으로 갱신한 시각이다. scope=ALL인데 " +
                "갱신 대상이 없었으면 null이다.",
        nullable = true,
    )
    val readAt: LocalDateTime?,
)
