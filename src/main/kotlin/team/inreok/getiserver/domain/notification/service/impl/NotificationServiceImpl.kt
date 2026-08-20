package team.inreok.getiserver.domain.notification.service.impl

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.notification.dto.NotificationCreateCommand
import team.inreok.getiserver.domain.notification.dto.NotificationListResponse
import team.inreok.getiserver.domain.notification.dto.NotificationReadRequest
import team.inreok.getiserver.domain.notification.dto.NotificationReadResponse
import team.inreok.getiserver.domain.notification.dto.NotificationReadScope
import team.inreok.getiserver.domain.notification.dto.UnreadNotificationCountResponse
import team.inreok.getiserver.domain.notification.entity.Notification
import team.inreok.getiserver.domain.notification.entity.type.NotificationType
import team.inreok.getiserver.domain.notification.exception.NotificationAccessDeniedException
import team.inreok.getiserver.domain.notification.exception.NotificationIdRequiredException
import team.inreok.getiserver.domain.notification.exception.NotificationNotFoundException
import team.inreok.getiserver.domain.notification.repository.NotificationRepository
import team.inreok.getiserver.domain.notification.service.NotificationService
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
        unreadOnly: Boolean,
        notificationType: NotificationType?,
        pageable: Pageable,
    ): NotificationListResponse {
        // 정렬은 Repository Query가 createdAt DESC, id DESC로 고정한다(원본 요구사항 문서 4절).
        // 클라이언트가 보낸 Sort를 그대로 넘기면 JPQL의 ORDER BY와 충돌하므로 Page 정보만 남긴다.
        val page =
            notificationRepository.findMyNotifications(
                memberId = memberId,
                // Notion 계약이 Boolean Flag이라 "읽은 것만" 조회는 표현할 수 없다. false는
                // "읽은 것만"이 아니라 "필터 없음"(null)이다.
                isRead = if (unreadOnly) false else null,
                type = notificationType,
                pageable = PageRequest.of(pageable.pageNumber, pageable.pageSize),
            )
        // 목록의 모든 대상을 한 번에 해석한다(대상 Domain당 조회 1회, N+1 방지).
        val availabilities = notificationTargetResolver.resolveAll(page.content.toTargetRefs(), memberId)
        // unreadCount는 Filter나 현재 Page와 무관한 전체 미읽음 수다(Header Badge 용도, 원본
        // 요구사항 문서 19절). Page content를 세지 않고 별도 COUNT Query를 쓴다.
        return page.toNotificationListResponse(availabilities, unreadCountOf(memberId))
    }

    @Transactional(readOnly = true)
    override fun countUnread(memberId: Long): UnreadNotificationCountResponse =
        UnreadNotificationCountResponse(unreadCount = unreadCountOf(memberId))

    @Transactional
    override fun read(
        memberId: Long,
        request: NotificationReadRequest,
    ): NotificationReadResponse =
        when (request.scope) {
            NotificationReadScope.SINGLE -> {
                // scope에 따라 필수 여부가 달라져 Bean Validation으로는 표현할 수 없다.
                readSingle(memberId, request.notificationId ?: throw NotificationIdRequiredException())
            }

            // notificationId가 함께 와도 무시한다(JobApplicationAdminActionRequest와 같은 관례).
            NotificationReadScope.ALL -> {
                readAll(memberId)
            }
        }

    @Transactional
    override fun delete(
        memberId: Long,
        notificationId: Long,
    ) {
        findOwnNotification(memberId, notificationId).softDelete(LocalDateTime.now())
    }

    private fun readSingle(
        memberId: Long,
        notificationId: Long,
    ): NotificationReadResponse {
        val notification = findOwnNotification(memberId, notificationId)
        // 이미 읽은 알림이면 markAsRead가 아무것도 바꾸지 않는다(멱등). 그 경우 updatedCount는
        // 0이고 readAt은 처음 읽은 시각이 그대로 유지된다.
        val alreadyRead = notification.isRead
        notification.markAsRead(LocalDateTime.now())

        return NotificationReadResponse(
            unreadCount = unreadCountOf(memberId),
            updatedCount = if (alreadyRead) 0L else 1L,
            readAt = notification.readAt,
        )
    }

    private fun readAll(memberId: Long): NotificationReadResponse {
        // 갱신된 모든 알림이 같은 readAt을 갖도록 시각을 한 번만 구해서 넘긴다.
        val readAt = LocalDateTime.now()
        val updatedCount = notificationRepository.markAllAsRead(memberId, readAt)
        return NotificationReadResponse(
            unreadCount = unreadCountOf(memberId),
            updatedCount = updatedCount.toLong(),
            // 바꾼 알림이 없으면 이 시각은 어떤 알림에도 기록되지 않았으므로 내려주지 않는다.
            readAt = if (updatedCount > 0) readAt else null,
        )
    }

    /**
     * 요청자 본인의 살아 있는 알림을 가져온다. 삭제된 알림은 목록에도 나오지 않으므로 없는 것과
     * 같이 취급하고, 다른 사용자의 알림은 403이다([NotificationAccessDeniedException] 참고).
     */
    private fun findOwnNotification(
        memberId: Long,
        notificationId: Long,
    ): Notification {
        val notification =
            notificationRepository
                .findById(notificationId)
                .orElseThrow { NotificationNotFoundException(notificationId) }
        if (notification.deletedAt != null) throw NotificationNotFoundException(notificationId)
        if (notification.recipientMemberId != memberId) throw NotificationAccessDeniedException()
        return notification
    }

    private fun unreadCountOf(memberId: Long): Long =
        notificationRepository.countByRecipientMemberIdAndIsReadFalseAndDeletedAtIsNull(memberId)

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
}
