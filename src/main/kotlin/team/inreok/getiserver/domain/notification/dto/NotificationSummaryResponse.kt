package team.inreok.getiserver.domain.notification.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetType
import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetUnavailableReason
import team.inreok.getiserver.domain.notification.entity.type.NotificationType
import java.time.LocalDateTime

@Schema(
    description =
        "알림 한 건. targetAvailable/targetUnavailableReason/deepLink는 저장된 값이 아니라 " +
            "요청 시점에 원본 리소스 상태를 확인해 서버가 계산한 값이다.",
)
data class NotificationSummaryResponse(
    @param:Schema(description = "알림 ID", example = "1")
    val notificationId: Long,
    @param:Schema(description = "알림 종류", example = "PROGRAM_PUBLISHED")
    val type: NotificationType,
    @param:Schema(description = "알림 제목", example = "새 프로그램이 게시되었습니다")
    val title: String,
    @param:Schema(description = "알림 본문", example = "AI 특강 프로그램 모집이 시작되었습니다.")
    val content: String,
    @param:Schema(description = "이동 대상 리소스의 종류", example = "PROGRAM", nullable = true)
    val targetType: NotificationTargetType?,
    @param:Schema(description = "이동 대상 리소스의 ID", example = "123", nullable = true)
    val targetId: Long?,
    @param:Schema(
        description = "지금 대상 리소스로 이동할 수 있는지 여부. 대상이 없는 알림은 항상 false다.",
        example = "true",
    )
    val targetAvailable: Boolean,
    @param:Schema(
        description =
            "이동할 수 없는 이유. targetAvailable=true이거나 아직 접근 가능 여부를 판정하지 " +
                "않는 대상이면 null이다.",
        example = "DELETED",
        nullable = true,
    )
    val targetUnavailableReason: NotificationTargetUnavailableReason?,
    @param:Schema(
        description = "이동 경로(상대 경로). 이동할 수 없으면 null이다.",
        example = "/programs/123",
        nullable = true,
    )
    val deepLink: String?,
    @param:Schema(description = "읽음 여부", example = "false")
    val isRead: Boolean,
    @param:Schema(description = "읽은 시각. 읽지 않았으면 null이다.", nullable = true)
    val readAt: LocalDateTime?,
    @param:Schema(description = "알림 생성 시각")
    val createdAt: LocalDateTime,
)
