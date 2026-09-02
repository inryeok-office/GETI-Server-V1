package team.inreok.getiserver.domain.notification.service.impl

import org.hibernate.exception.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
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
import team.inreok.getiserver.domain.notification.service.PushDeliveryService
import java.time.LocalDateTime

@Service
class NotificationServiceImpl(
    private val notificationRepository: NotificationRepository,
    private val notificationTargetResolver: NotificationTargetResolver,
    private val notificationInsertOperation: NotificationInsertOperation,
    private val pushDeliveryService: PushDeliveryService,
) : NotificationService {
    private val log = LoggerFactory.getLogger(NotificationServiceImpl::class.java)

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
     * 항상 새 Transaction에서 저장한다(Issue #118). 실제 Insert는 [NotificationInsertOperation]에
     * 위임한다 -- `REQUIRES_NEW`가 필요한 이유와 이 Class를 분리한 이유는 그 KDoc 참고.
     *
     * 같은 Idempotency Identity(recipientMemberId + sourceEventType + sourceEventId, Issue #193)로
     * 이미 알림이 있으면 UNIQUE 제약(`uk_notifications_recipient_source_event`) 위반으로 Insert가
     * 실패하는데, 이를 오류가 아니라 **조용한 No-op**으로 처리하고 기존 알림의 id를 그대로
     * 돌려준다 -- 지금까지 어떤 호출자(4개 Listener)도 반환값을 쓰지 않지만, 이 Method의 계약
     * ("항상 유효한 알림 id를 돌려준다")을 유지하기 위해서다. UNIQUE 제약과 무관한
     * `DataIntegrityViolationException`(예: 다른 Column 길이 제한 위반)까지 "이미 존재함"으로
     * 오판하면 실제 저장 실패가 조용히 묻히므로, 제약 이름을 확인해 이 Identity 충돌일 때만
     * No-op으로 처리한다(`DiscordDeliveryServiceImpl.enqueue`/`JobNotificationServiceImpl
     * .createDeliveryOrNull`과 같은 방식).
     */
    override fun create(command: NotificationCreateCommand): Long =
        try {
            val notificationId = notificationInsertOperation.insert(command)
            // 방금 새로 만든 알림에만 Push를 예약한다 -- 아래 UNIQUE 제약으로 기존 알림을 재사용한
            // 경우(같은 알림이 이미 존재)는 이미 한 번 예약이 끝났으므로 다시 보내지 않는다.
            //
            // Push 발송은 인앱 알림 생성의 부가 기능이다(Issue #190) -- 예약 중 어떤 오류가 나도
            // 이미 만든 알림 id를 그대로 돌려주고 이 Method가 실패한 것처럼 보이지 않게 한다
            // (`InquiryAnsweredNotificationListener` 등 4개 Listener가 `create` 호출을
            // `runCatching`으로 감싸는 것과 같은 원칙).
            runCatching {
                pushDeliveryService.enqueueForNotification(notificationId, command.recipientMemberId, command.type)
            }.onFailure { ex ->
                log.error("Push 전달 예약 중 처리되지 않은 오류(notificationId={})", notificationId, ex)
            }
            notificationId
        } catch (ex: DataIntegrityViolationException) {
            if (!isSourceEventDuplicate(ex)) throw ex
            findExistingBySourceEvent(command) ?: throw ex
        }

    private fun findExistingBySourceEvent(command: NotificationCreateCommand): Long? =
        notificationRepository
            .findByRecipientMemberIdAndSourceEventTypeAndSourceEventId(
                recipientMemberId = command.recipientMemberId,
                sourceEventType = command.sourceEventType,
                sourceEventId = command.sourceEventId,
            )?.id

    private fun isSourceEventDuplicate(ex: DataIntegrityViolationException): Boolean {
        val constraintName =
            generateSequence<Throwable>(ex) { it.cause }
                .filterIsInstance<ConstraintViolationException>()
                .firstOrNull()
                ?.constraintName
        return constraintName?.equals(SOURCE_EVENT_UNIQUE_CONSTRAINT, ignoreCase = true) == true ||
            ex.message?.contains(SOURCE_EVENT_UNIQUE_CONSTRAINT, ignoreCase = true) == true
    }

    private companion object {
        const val SOURCE_EVENT_UNIQUE_CONSTRAINT = "uk_notifications_recipient_source_event"
    }
}
