package team.inreok.getiserver.domain.notification.event

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import team.inreok.getiserver.domain.inquiry.event.InquiryAnsweredEvent
import team.inreok.getiserver.domain.notification.dto.NotificationCreateCommand
import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetType
import team.inreok.getiserver.domain.notification.entity.type.NotificationType
import team.inreok.getiserver.domain.notification.service.NotificationService

/**
 * Inquiry가 발행하는 [InquiryAnsweredEvent]를 받아 문의 작성자에게 인앱 알림을 만든다(Notification
 * 도메인 개발 요구사항 5절). Commit 이후에만 실행되어(`AFTER_COMMIT`) 아직 반영되지 않은 답변을
 * 알리지 않는다 -- [team.inreok.getiserver.domain.search.event.JobIndexSyncEventListener]와 동일한
 * 방식과 이유다.
 *
 * 이 Listener가 실패해도(알림 저장 실패 등) 이미 Commit된 답변 등록 Transaction에는 영향을 주지
 * 않는다 -- `AFTER_COMMIT` 시점에는 원본 Transaction이 이미 끝났기 때문이다. 예외를 다시 던지지
 * 않고 로그만 남기는 이유도 같다(재시도는 이번 Phase 범위가 아니다).
 */
@Component
class InquiryAnsweredNotificationListener(
    private val notificationService: NotificationService,
) {
    private val log = LoggerFactory.getLogger(InquiryAnsweredNotificationListener::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onInquiryAnswered(event: InquiryAnsweredEvent) {
        runCatching {
            notificationService.create(
                NotificationCreateCommand(
                    recipientMemberId = event.inquiryAuthorMemberId,
                    type = NotificationType.INQUIRY_ANSWERED,
                    title = "문의에 답변이 등록되었습니다",
                    content = "\"${event.inquiryTitle}\" 문의에 답변이 등록되었습니다.",
                    targetType = NotificationTargetType.INQUIRY,
                    targetId = event.inquiryId,
                ),
            )
        }.onFailure { ex ->
            log.error(
                "문의 답변 알림 생성 중 처리되지 않은 오류(inquiryId={}, answerId={})",
                event.inquiryId,
                event.answerId,
                ex,
            )
        }
    }
}
