package team.inreok.getiserver.domain.notification.service.impl

import org.springframework.data.domain.Page
import team.inreok.getiserver.domain.notification.dto.NotificationListResponse
import team.inreok.getiserver.domain.notification.dto.NotificationSummaryResponse
import team.inreok.getiserver.domain.notification.entity.Notification
import team.inreok.getiserver.domain.notification.service.NotificationTargetAvailability
import team.inreok.getiserver.domain.notification.service.NotificationTargetRef

/**
 * `Notification` Entity를 목록 응답 DTO로 변환한다. detekt TooManyFunctions 한도 안에서
 * [NotificationServiceImpl]을 유지하기 위해 순수 함수로 분리했다
 * (`JobApplicationResponseMapping.kt`와 같은 이유).
 *
 * [availabilities]는 호출부가 [team.inreok.getiserver.domain.notification.service.NotificationTargetResolver]로
 * 미리 한 번에 조회해 전달한다 -- 이 함수를 순수 함수로 유지하고 항목마다 대상 Domain을 다시
 * 조회하지 않기 위해서다(N+1 방지).
 */
fun Page<Notification>.toNotificationListResponse(
    availabilities: Map<NotificationTargetRef, NotificationTargetAvailability>,
    unreadCount: Long,
): NotificationListResponse =
    NotificationListResponse(
        content = content.map { it.toNotificationSummary(availabilities) },
        page = number,
        size = size,
        totalElements = totalElements,
        totalPages = totalPages,
        first = isFirst,
        last = isLast,
        unreadCount = unreadCount,
    )

/** 목록에 담긴 알림들이 가리키는 대상을 중복 없이 모은다. 대상이 없는 알림은 빠진다. */
fun List<Notification>.toTargetRefs(): Set<NotificationTargetRef> =
    mapNotNull { notification ->
        val targetType = notification.targetType ?: return@mapNotNull null
        val targetId = notification.targetId ?: return@mapNotNull null
        NotificationTargetRef(targetType, targetId)
    }.toSet()

private fun Notification.toNotificationSummary(
    availabilities: Map<NotificationTargetRef, NotificationTargetAvailability>,
): NotificationSummaryResponse {
    val availability =
        targetType?.let { type ->
            targetId?.let { id -> availabilities[NotificationTargetRef(type, id)] }
        }
    return NotificationSummaryResponse(
        notificationId = requireNotNull(id) { "저장된 Notification은 id를 가져야 합니다." },
        notificationType = type,
        title = title,
        content = content,
        targetType = targetType,
        targetId = targetId,
        // 대상이 없는 알림(SYSTEM 등)은 이동할 곳이 없으므로 항상 false다.
        targetAvailable = availability?.available ?: false,
        targetUnavailableReason = availability?.reason,
        deepLink = availability?.deepLink,
        read = isRead,
        readAt = readAt,
        createdAt = requireNotNull(createdAt) { "저장된 Notification은 createdAt을 가져야 합니다." },
    )
}
