package team.inreok.getiserver.domain.notification.event

import org.slf4j.LoggerFactory
import org.springframework.core.task.TaskExecutor
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import team.inreok.getiserver.domain.member.event.MemberApprovalProcessedEvent
import team.inreok.getiserver.domain.notification.dto.NotificationCreateCommand
import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetType
import team.inreok.getiserver.domain.notification.entity.type.NotificationType
import team.inreok.getiserver.domain.notification.service.NotificationService

/**
 * Member가 발행하는 [MemberApprovalProcessedEvent]를 받아 승인/거절 대상 회원에게 인앱 알림을
 * 만든다(Issue #118, 제품 계약 Notification §16.1). 구조와 실패 처리 방침은
 * [InquiryAnsweredNotificationListener]와 같다 -- `AFTER_COMMIT`이라 상태 전이가 Rollback되면
 * 알림도 만들어지지 않고, 반대로 알림 저장이 실패해도 이미 Commit된 승인/거절에는 영향이 없다.
 *
 * `targetType`은 [NotificationTargetType.MEMBER_APPROVAL]로 두되
 * [team.inreok.getiserver.domain.notification.service.NotificationTargetResolver]에는 등록하지
 * 않는다 -- 승인 결과는 이동해서 볼 별도 리소스 화면이 없어(제목·본문이 결과 전부다) 지금
 * 해석기를 붙이면 없는 Deep Link를 지어내는 셈이 된다. Resolver는 이 대상을 "이동 불가, 이유
 * 없음"으로 내려주며, 이는 그 KDoc이 명시한 기본 동작이다.
 *
 * 알림 생성은 [ProgramDeletedNotificationListener]와 동일하게 전용 `notificationTaskExecutor`로
 * 넘겨 승인/거절 API 응답을 막지 않는다(PR #128 리뷰 지적). 이쪽은 수신자가 1명이라 지연이
 * 작지만, 같은 Module의 두 Listener가 서로 다른 실행 방식을 갖지 않도록 맞춘다.
 */
@Component
class MemberApprovalProcessedNotificationListener(
    private val notificationService: NotificationService,
    private val notificationTaskExecutor: TaskExecutor,
) {
    private val log = LoggerFactory.getLogger(MemberApprovalProcessedNotificationListener::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onMemberApprovalProcessed(event: MemberApprovalProcessedEvent) {
        notificationTaskExecutor.execute { createNotification(event) }
    }

    private fun createNotification(event: MemberApprovalProcessedEvent) {
        runCatching {
            notificationService.create(
                NotificationCreateCommand(
                    recipientMemberId = event.memberId,
                    type = NotificationType.MEMBER_APPROVAL_RESULT,
                    title = if (event.approved) APPROVED_TITLE else REJECTED_TITLE,
                    content = contentOf(event),
                    // 교직원 가입 승인/거절은 PENDING 회원에게만 한 번 일어나고(승인/거절 이후
                    // 다시 PENDING으로 돌아가는 경로가 없다, MemberApprovalServiceImpl 참고) 한
                    // 회원 생애주기에 최대 1건만 발행되므로 memberId 자체가 안정적인 식별자다.
                    sourceEventType = SOURCE_EVENT_TYPE,
                    sourceEventId = event.memberId,
                    targetType = NotificationTargetType.MEMBER_APPROVAL,
                    targetId = event.memberId,
                ),
            )
        }.onFailure { ex ->
            log.error(
                "교직원 승인 결과 알림 생성 중 처리되지 않은 오류(memberId={}, approved={})",
                event.memberId,
                event.approved,
                ex,
            )
        }
    }

    /**
     * 거절 사유는 개발자가 입력한 값이라 그대로 싣는다 -- 사유를 감추면 회원이 무엇을 보완해야
     * 하는지 알 수 없어 알림의 목적 자체가 사라진다. 사유가 비어 있는 경우는 발행 측
     * (`MemberApprovalServiceImpl.reject`)이 400으로 막지만, Event 계약상 nullable이므로 방어적으로
     * 사유 없는 문구를 준비한다.
     */
    private fun contentOf(event: MemberApprovalProcessedEvent): String {
        if (event.approved) return APPROVED_CONTENT
        val reason = event.rejectionReason?.takeIf { it.isNotBlank() }
        return if (reason == null) REJECTED_CONTENT_WITHOUT_REASON else "$REJECTED_CONTENT_WITHOUT_REASON 사유: $reason"
    }

    companion object {
        private const val APPROVED_TITLE = "교직원 가입이 승인되었습니다"
        private const val REJECTED_TITLE = "교직원 가입이 거절되었습니다"
        private const val APPROVED_CONTENT = "교직원 가입 신청이 승인되었습니다. 이제 교직원 기능을 사용할 수 있습니다."
        private const val REJECTED_CONTENT_WITHOUT_REASON = "교직원 가입 신청이 거절되었습니다."

        // Notification Idempotency Identity(Issue #193)의 sourceEventType. 원본 Domain Event Class
        // 이름을 그대로 쓴다(InquiryAnsweredNotificationListener와 같은 이유).
        private const val SOURCE_EVENT_TYPE = "MemberApprovalProcessedEvent"
    }
}
