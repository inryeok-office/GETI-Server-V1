package team.inreok.getiserver.domain.notification.event

import org.slf4j.LoggerFactory
import org.springframework.core.task.TaskExecutor
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import team.inreok.getiserver.domain.notification.dto.NotificationCreateCommand
import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetType
import team.inreok.getiserver.domain.notification.entity.type.NotificationType
import team.inreok.getiserver.domain.notification.service.NotificationService
import team.inreok.getiserver.domain.program.event.ProgramDeletedEvent
import team.inreok.getiserver.domain.program.query.ProgramApplicantQueryPort

/**
 * Program이 발행하는 [ProgramDeletedEvent]를 받아 신청자들에게 인앱 알림을 만든다(Issue #118,
 * 제품 계약 Notification §16.1). 구조와 실패 처리 방침은 [InquiryAnsweredNotificationListener]와
 * 같다.
 *
 * `targetType`은 [NotificationTargetType.PROGRAM]으로 두어 목록 조회 시
 * [team.inreok.getiserver.domain.notification.service.NotificationTargetResolver]가 삭제된
 * 프로그램으로 판정하게 한다 -- 알림에서 이동을 시도하면 `targetAvailable=false`,
 * `targetUnavailableReason=DELETED`로 내려가 클라이언트가 "삭제된 프로그램"임을 표시할 수 있다.
 *
 * 수신자 수만큼 Insert가 이어지므로 실제 알림 생성은 전용 `notificationTaskExecutor`로 넘긴다
 * (PR #128 리뷰 지적). `AFTER_COMMIT` Listener는 원본 Transaction을 Commit하는 같은 호출
 * 흐름에서 동기 실행되어, 그대로 두면 삭제 API 응답이 신청자 수에 비례해 느려진다
 * ([team.inreok.getiserver.domain.search.event.JobIndexSyncEventListener]와 동일한 이유·방식).
 */
@Component
class ProgramDeletedNotificationListener(
    private val programApplicantQueryPort: ProgramApplicantQueryPort,
    private val notificationService: NotificationService,
    private val notificationTaskExecutor: TaskExecutor,
) {
    private val log = LoggerFactory.getLogger(ProgramDeletedNotificationListener::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onProgramDeleted(event: ProgramDeletedEvent) {
        notificationTaskExecutor.execute { createNotifications(event) }
    }

    private fun createNotifications(event: ProgramDeletedEvent) {
        val applicantMemberIds =
            runCatching { programApplicantQueryPort.findActiveApplicantMemberIds(event.programId) }
                .getOrElse { ex ->
                    log.error("삭제된 프로그램의 신청자 조회 실패(programId={})", event.programId, ex)
                    return
                }

        // 한 수신자의 알림 저장이 실패해도 나머지 수신자는 알림을 받아야 하므로 개별로 감싼다
        // (수신자 수가 정해져 있지 않아, 한 건 실패로 전체를 중단하면 손실이 커진다).
        applicantMemberIds.distinct().forEach { memberId ->
            runCatching { createNotification(event, memberId) }
                .onFailure { ex ->
                    log.error(
                        "프로그램 삭제 알림 생성 중 처리되지 않은 오류(programId={}, memberId={})",
                        event.programId,
                        memberId,
                        ex,
                    )
                }
        }
    }

    private fun createNotification(
        event: ProgramDeletedEvent,
        recipientMemberId: Long,
    ) {
        notificationService.create(
            NotificationCreateCommand(
                recipientMemberId = recipientMemberId,
                type = NotificationType.PROGRAM_DELETED,
                title = "신청한 프로그램이 삭제되었습니다",
                content = "\"${event.title}\" 프로그램이 삭제되어 신청이 더 이상 유효하지 않습니다.",
                // DELETED는 종단 상태라(allowedTransitions(DELETED) == emptySet(),
                // ProgramServiceImpl 참고) 한 프로그램은 생애주기에 최대 한 번만 삭제된다. 따라서
                // programId 자체가 안정적인 식별자다.
                sourceEventType = SOURCE_EVENT_TYPE,
                sourceEventId = event.programId,
                targetType = NotificationTargetType.PROGRAM,
                targetId = event.programId,
            ),
        )
    }

    private companion object {
        // Notification Idempotency Identity(Issue #193)의 sourceEventType. 원본 Domain Event Class
        // 이름을 그대로 쓴다(InquiryAnsweredNotificationListener와 같은 이유).
        const val SOURCE_EVENT_TYPE = "ProgramDeletedEvent"
    }
}
