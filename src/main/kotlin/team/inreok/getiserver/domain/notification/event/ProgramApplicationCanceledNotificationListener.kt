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
import team.inreok.getiserver.domain.program.event.ProgramApplicationCanceledEvent
import team.inreok.getiserver.domain.program.query.ProgramManagerQueryPort

/**
 * Program이 발행하는 [ProgramApplicationCanceledEvent]를 받아 취소한 학생 본인과 담당 교사에게
 * `PROGRAM_APPLICATION_CANCELED` 인앱 알림을 만든다(Issue #191, 제품 계약 확정). 구조는
 * [ProgramApplicationAppliedNotificationListener]와 같다 -- 취소 사유는 Program 계약상 받지
 * 않으므로 알림 내용에도 포함하지 않는다.
 */
@Component
class ProgramApplicationCanceledNotificationListener(
    private val programManagerQueryPort: ProgramManagerQueryPort,
    private val notificationService: NotificationService,
    private val notificationTaskExecutor: TaskExecutor,
) {
    private val log = LoggerFactory.getLogger(ProgramApplicationCanceledNotificationListener::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onProgramApplicationCanceled(event: ProgramApplicationCanceledEvent) {
        notificationTaskExecutor.execute { createNotifications(event) }
    }

    private fun createNotifications(event: ProgramApplicationCanceledEvent) {
        runCatching { createStudentNotification(event) }
            .onFailure { ex ->
                log.error(
                    "프로그램 신청 취소 알림(학생) 생성 중 처리되지 않은 오류(programId={}, applicationId={})",
                    event.programId,
                    event.applicationId,
                    ex,
                )
            }

        val managerMemberId =
            runCatching { programManagerQueryPort.findById(event.programId)?.managerMemberId }
                .getOrElse { ex ->
                    log.error("담당 교사 조회 실패(programId={})", event.programId, ex)
                    null
                }
        if (managerMemberId == null) return

        runCatching { createManagerNotification(event, managerMemberId) }
            .onFailure { ex ->
                log.error(
                    "프로그램 신청 취소 알림(교사) 생성 중 처리되지 않은 오류(programId={}, applicationId={})",
                    event.programId,
                    event.applicationId,
                    ex,
                )
            }
    }

    private fun createStudentNotification(event: ProgramApplicationCanceledEvent) {
        notificationService.create(
            NotificationCreateCommand(
                recipientMemberId = event.applicantMemberId,
                type = NotificationType.PROGRAM_APPLICATION_CANCELED,
                title = "프로그램 신청이 취소되었습니다",
                content = "\"${event.programTitle}\" 프로그램 신청이 취소되었습니다.",
                sourceEventType = SOURCE_EVENT_TYPE,
                sourceEventId = event.applicationId,
                targetType = NotificationTargetType.PROGRAM,
                targetId = event.programId,
            ),
        )
    }

    private fun createManagerNotification(
        event: ProgramApplicationCanceledEvent,
        managerMemberId: Long,
    ) {
        notificationService.create(
            NotificationCreateCommand(
                recipientMemberId = managerMemberId,
                type = NotificationType.PROGRAM_APPLICATION_CANCELED,
                title = "프로그램 신청이 취소되었습니다",
                content = "\"${event.programTitle}\" 프로그램에 신청했던 학생이 신청을 취소했습니다.",
                sourceEventType = SOURCE_EVENT_TYPE,
                sourceEventId = event.applicationId,
                targetType = NotificationTargetType.PROGRAM,
                targetId = event.programId,
            ),
        )
    }

    private companion object {
        const val SOURCE_EVENT_TYPE = "ProgramApplicationCanceledEvent"
    }
}
