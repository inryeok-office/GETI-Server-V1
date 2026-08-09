package team.inreok.getiserver.domain.notification.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "전체 알림 읽음 처리 결과")
data class NotificationReadAllResponse(
    @param:Schema(description = "이번 요청으로 읽음 처리된 알림 개수. 이미 모두 읽었다면 0이다.", example = "3")
    val updatedCount: Long,
    @param:Schema(description = "읽음 처리에 사용한 시각. 이번 요청으로 갱신된 알림이 모두 이 값을 갖는다.")
    val readAt: LocalDateTime,
)
