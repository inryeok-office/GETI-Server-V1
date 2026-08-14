package team.inreok.getiserver.domain.notification.service.impl

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.notification.dto.NotificationCreateCommand
import team.inreok.getiserver.domain.notification.dto.NotificationListResponse
import team.inreok.getiserver.domain.notification.dto.NotificationReadAllResponse
import team.inreok.getiserver.domain.notification.dto.NotificationReadResponse
import team.inreok.getiserver.domain.notification.dto.NotificationSummaryResponse
import team.inreok.getiserver.domain.notification.dto.UnreadNotificationCountResponse
import team.inreok.getiserver.domain.notification.entity.Notification
import team.inreok.getiserver.domain.notification.entity.type.NotificationType
import team.inreok.getiserver.domain.notification.exception.NotificationAccessDeniedException
import team.inreok.getiserver.domain.notification.exception.NotificationNotFoundException
import team.inreok.getiserver.domain.notification.repository.NotificationRepository
import team.inreok.getiserver.domain.notification.service.NotificationService
import team.inreok.getiserver.domain.notification.service.NotificationTargetAvailability
import team.inreok.getiserver.domain.notification.service.NotificationTargetRef
import team.inreok.getiserver.domain.notification.service.NotificationTargetResolver
import java.time.LocalDateTime

@Service
class NotificationServiceImpl(
    private val notificationRepository: NotificationRepository,
    private val notificationTargetResolver: NotificationTargetResolver,
) : NotificationService {
    @Transactional(readOnly = true)
    override fun list(
        memberId: Long,
        isRead: Boolean?,
        type: NotificationType?,
        pageable: Pageable,
    ): NotificationListResponse {
        // 정렬은 Repository Query가 createdAt DESC, id DESC로 고정한다(원본 요구사항 문서 4절).
        // 클라이언트가 보낸 Sort를 그대로 넘기면 JPQL의 ORDER BY와 충돌하므로 Page 정보만 남긴다.
        val page =
            notificationRepository.findMyNotifications(
                memberId = memberId,
                isRead = isRead,
                type = type,
                pageable = PageRequest.of(pageable.pageNumber, pageable.pageSize),
            )
        return page.toListResponse(memberId)
    }

    @Transactional(readOnly = true)
    override fun countUnread(memberId: Long): UnreadNotificationCountResponse =
        UnreadNotificationCountResponse(
            unreadCount = notificationRepository.countByRecipientMemberIdAndIsReadFalseAndDeletedAtIsNull(memberId),
        )

    @Transactional
    override fun markAsRead(
        memberId: Long,
        notificationId: Long,
    ): NotificationReadResponse {
        val notification =
            notificationRepository
                .findById(notificationId)
                .orElseThrow { NotificationNotFoundException(notificationId) }
        // 삭제된 알림은 목록에도 나오지 않으므로 없는 것과 같이 취급한다.
        if (notification.deletedAt != null) throw NotificationNotFoundException(notificationId)
        if (notification.recipientMemberId != memberId) throw NotificationAccessDeniedException()

        notification.markAsRead(LocalDateTime.now())

        return NotificationReadResponse(
            notificationId = notificationId,
            isRead = true,
            readAt = requireNotNull(notification.readAt) { "읽음 처리된 알림은 readAt을 가져야 합니다." },
        )
    }

    @Transactional
    override fun markAllAsRead(memberId: Long): NotificationReadAllResponse {
        // 갱신된 모든 알림이 같은 readAt을 갖도록 시각을 한 번만 구해서 넘긴다.
        val readAt = LocalDateTime.now()
        val updatedCount = notificationRepository.markAllAsRead(memberId, readAt)
        return NotificationReadAllResponse(updatedCount = updatedCount.toLong(), readAt = readAt)
    }

    /**
     * 항상 새 Transaction에서 저장한다(Issue #118).
     *
     * 이 Method의 유일한 호출자는 `@TransactionalEventListener(AFTER_COMMIT)` Listener들이다. 그
     * 시점에는 원본 Transaction이 **이미 Commit됐지만 Thread에 아직 바인딩된 상태**라, 기본
     * `REQUIRED`로 두면 새 Insert가 그 끝난 Transaction에 참여한다 -- 참여 Transaction은 스스로
     * Commit하지 않고 바깥 Transaction은 이미 Commit을 마쳤으므로, 예외 하나 없이 Row가 사라진다
     * (Issue #118이 보고한 "알림이 생성되지 않는" 증상의 직접 원인이다).
     *
     * `AFTER_COMMIT` Listener는 원본 Transaction이 Rollback되면 아예 실행되지 않으므로,
     * `REQUIRES_NEW`로 바꿔도 "원본이 실패하면 알림도 없다"라는 일관성은 그대로 유지된다.
     * 수신자가 여럿인 알림(프로그램 삭제 등)에서 한 건의 저장 실패가 나머지 수신자의 알림까지
     * 되돌리지 않는 효과도 함께 얻는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun create(command: NotificationCreateCommand): Long {
        val notification =
            Notification(
                recipientMemberId = command.recipientMemberId,
                type = command.type,
                title = command.title,
                content = command.content,
            ).apply {
                targetType = command.targetType
                targetId = command.targetId
            }
        val saved = notificationRepository.save(notification)
        return requireNotNull(saved.id) { "저장된 Notification은 id를 가져야 합니다." }
    }

    private fun Page<Notification>.toListResponse(viewerMemberId: Long): NotificationListResponse {
        val availabilities = resolveTargets(content, viewerMemberId)
        return NotificationListResponse(
            content = content.map { it.toSummary(availabilities) },
            page = number,
            size = size,
            totalElements = totalElements,
            totalPages = totalPages,
            first = isFirst,
            last = isLast,
        )
    }

    /** 목록의 모든 대상을 한 번에 해석한다(대상 Domain당 조회 1회, N+1 방지). */
    private fun resolveTargets(
        notifications: List<Notification>,
        viewerMemberId: Long,
    ): Map<NotificationTargetRef, NotificationTargetAvailability> {
        val refs =
            notifications
                .mapNotNull { notification ->
                    val targetType = notification.targetType ?: return@mapNotNull null
                    val targetId = notification.targetId ?: return@mapNotNull null
                    NotificationTargetRef(targetType, targetId)
                }.toSet()
        return notificationTargetResolver.resolveAll(refs, viewerMemberId)
    }

    private fun Notification.toSummary(
        availabilities: Map<NotificationTargetRef, NotificationTargetAvailability>,
    ): NotificationSummaryResponse {
        val availability =
            targetType?.let { type ->
                targetId?.let { id -> availabilities[NotificationTargetRef(type, id)] }
            }
        return NotificationSummaryResponse(
            notificationId = requireNotNull(id) { "저장된 Notification은 id를 가져야 합니다." },
            type = type,
            title = title,
            content = content,
            targetType = targetType,
            targetId = targetId,
            // 대상이 없는 알림(SYSTEM 등)은 이동할 곳이 없으므로 항상 false다.
            targetAvailable = availability?.available ?: false,
            targetUnavailableReason = availability?.reason,
            deepLink = availability?.deepLink,
            isRead = isRead,
            readAt = readAt,
            createdAt = requireNotNull(createdAt) { "저장된 Notification은 createdAt을 가져야 합니다." },
        )
    }
}
