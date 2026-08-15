package team.inreok.getiserver.domain.notification.event

import org.slf4j.LoggerFactory
import org.springframework.core.task.TaskExecutor
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import team.inreok.getiserver.domain.application.event.JobApplicationReviewedEvent
import team.inreok.getiserver.domain.notification.dto.NotificationCreateCommand
import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetType
import team.inreok.getiserver.domain.notification.entity.type.NotificationType
import team.inreok.getiserver.domain.notification.service.NotificationService

/**
 * Application이 발행하는 [JobApplicationReviewedEvent]를 받아 지원자 본인에게
 * `JOB_APPLICATION_STATUS_CHANGED` 인앱 알림을 만든다(Issue #135). 구조와 실패 처리 방침은
 * [MemberApprovalProcessedNotificationListener]와 같다 -- `AFTER_COMMIT`이라 검토 Action이
 * Rollback되면 알림도 만들어지지 않고, 반대로 알림 저장이 실패해도 이미 Commit된 검토 결과에는
 * 영향이 없다.
 *
 * `targetType`은 [NotificationTargetType.JOB_APPLICATION]으로 두지만
 * [team.inreok.getiserver.domain.notification.service.NotificationTargetResolver]/
 * [team.inreok.getiserver.domain.notification.service.NotificationDeepLink]는 아직 이 대상을
 * 해석하지 않는다(둘 다 "Event 연결 시점에 함께 붙인다"고 남겨뒀지만, 실제로 붙이려면 "지원서
 * 본인만 열람 가능"이라는 소유자 기반 판정이 새로 필요하다 -- 지금까지 어떤 대상도 이 판정을 하지
 * 않았다). 알림의 제목·본문만으로 결과를 전달할 수 있어(`MEMBER_APPROVAL_RESULT`와 동일한 상황),
 * 이 Issue 범위에서는 Deep Link 없이 `available=false`로 내려가는 것을 그대로 두었다. 소유자 기반
 * 판정이 필요한 첫 사례라 이번 PR에서 임의로 설계하지 않는다.
 *
 * 알림 생성은 [MemberApprovalProcessedNotificationListener]와 동일하게 전용
 * `notificationTaskExecutor`로 넘겨 검토 Action API 응답을 막지 않는다(PR #128 리뷰 지적).
 */
@Component
class JobApplicationReviewedNotificationListener(
    private val notificationService: NotificationService,
    private val notificationTaskExecutor: TaskExecutor,
) {
    private val log = LoggerFactory.getLogger(JobApplicationReviewedNotificationListener::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onJobApplicationReviewed(event: JobApplicationReviewedEvent) {
        notificationTaskExecutor.execute { createNotification(event) }
    }

    private fun createNotification(event: JobApplicationReviewedEvent) {
        runCatching {
            notificationService.create(
                NotificationCreateCommand(
                    recipientMemberId = event.studentMemberId,
                    type = NotificationType.JOB_APPLICATION_STATUS_CHANGED,
                    title = titleOf(event.action),
                    content = contentOf(event),
                    targetType = NotificationTargetType.JOB_APPLICATION,
                    targetId = event.applicationId,
                ),
            )
        }.onFailure { ex ->
            log.error(
                "지원서 검토 알림 생성 중 처리되지 않은 오류(applicationId={}, action={})",
                event.applicationId,
                event.action,
                ex,
            )
        }
    }

    private fun titleOf(action: String): String =
        when (action) {
            "ALLOW_EDIT" -> "지원서 수정이 허용되었습니다"
            "REQUEST_REVISION" -> "지원서 보완이 필요합니다"
            "APPROVE" -> "지원이 승인되었습니다"
            "REJECT" -> "지원이 거절되었습니다"
            else -> "지원서 상태가 변경되었습니다"
        }

    /**
     * 거절·보완 요청 사유는 교사가 입력한 값이라 그대로 싣는다 -- 사유를 감추면 학생이 무엇을
     * 보완해야 하는지 알 수 없어 알림의 목적 자체가 사라진다
     * (`MemberApprovalProcessedNotificationListener.contentOf`와 동일한 판단).
     */
    private fun contentOf(event: JobApplicationReviewedEvent): String {
        val reason = event.reason?.takeIf { it.isNotBlank() }
        return when (event.action) {
            "ALLOW_EDIT" -> "지원서 수정 요청이 승인되었습니다. 내용을 수정해 다시 제출해주세요."
            "REQUEST_REVISION" -> reasonedContent("제출한 지원서에 보완이 필요합니다.", reason)
            "APPROVE" -> "지원이 승인되었습니다."
            "REJECT" -> reasonedContent("지원이 거절되었습니다.", reason)
            else -> "지원서 상태가 변경되었습니다."
        }
    }

    private fun reasonedContent(
        base: String,
        reason: String?,
    ): String = if (reason == null) base else "$base 사유: $reason"
}
