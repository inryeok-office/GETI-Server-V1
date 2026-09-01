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
import team.inreok.getiserver.domain.program.event.ProgramApplicationAppliedEvent
import team.inreok.getiserver.domain.program.query.ProgramManagerQueryPort

/**
 * Program이 발행하는 [ProgramApplicationAppliedEvent]를 받아 신청 학생 본인과 담당 교사에게
 * `PROGRAM_APPLICATION_APPLIED` 인앱 알림을 만든다(Issue #191, 제품 계약 확정). 구조와 실패
 * 처리 방침은 [ProgramDeletedNotificationListener]와 같다.
 *
 * 담당 교사는 [ProgramManagerQueryPort]로 조회한다(Issue #191 "구현 시 확인 사항"이 재사용
 * 가능성을 언급한 그 Port). `managerMemberId`가 아직 없으면(이론상 프로그램은 PUBLISHED
 * 전이 시점에 항상 채워지지만 방어적으로) 교사 알림을 만들지 않고 학생 알림만 만든다.
 *
 * 학생과 교사는 recipientMemberId가 서로 달라 같은 `sourceEventType`(applicationId)을 써도
 * Notification Idempotency Identity(Issue #193)가 충돌하지 않는다.
 */
@Component
class ProgramApplicationAppliedNotificationListener(
    private val programManagerQueryPort: ProgramManagerQueryPort,
    private val notificationService: NotificationService,
    private val notificationTaskExecutor: TaskExecutor,
) {
    private val log = LoggerFactory.getLogger(ProgramApplicationAppliedNotificationListener::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onProgramApplicationApplied(event: ProgramApplicationAppliedEvent) {
        notificationTaskExecutor.execute { createNotifications(event) }
    }

    private fun createNotifications(event: ProgramApplicationAppliedEvent) {
        runCatching { createStudentNotification(event) }
            .onFailure { ex ->
                log.error(
                    "프로그램 신청 알림(학생) 생성 중 처리되지 않은 오류(programId={}, applicationId={})",
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
                    "프로그램 신청 알림(교사) 생성 중 처리되지 않은 오류(programId={}, applicationId={})",
                    event.programId,
                    event.applicationId,
                    ex,
                )
            }
    }

    private fun createStudentNotification(event: ProgramApplicationAppliedEvent) {
        notificationService.create(
            NotificationCreateCommand(
                recipientMemberId = event.applicantMemberId,
                type = NotificationType.PROGRAM_APPLICATION_APPLIED,
                title = "프로그램 신청이 접수되었습니다",
                content = "\"${event.programTitle}\" 프로그램 신청이 정상적으로 접수되었습니다.",
                sourceEventType = SOURCE_EVENT_TYPE,
                sourceEventId = event.applicationId,
                targetType = NotificationTargetType.PROGRAM,
                targetId = event.programId,
            ),
        )
    }

    private fun createManagerNotification(
        event: ProgramApplicationAppliedEvent,
        managerMemberId: Long,
    ) {
        notificationService.create(
            NotificationCreateCommand(
                recipientMemberId = managerMemberId,
                type = NotificationType.PROGRAM_APPLICATION_APPLIED,
                title = "새로운 프로그램 신청이 접수되었습니다",
                content = "\"${event.programTitle}\" 프로그램에 새로운 신청이 접수되었습니다.",
                sourceEventType = SOURCE_EVENT_TYPE,
                sourceEventId = event.applicationId,
                targetType = NotificationTargetType.PROGRAM,
                targetId = event.programId,
            ),
        )
    }

    private companion object {
        // Notification Idempotency Identity(Issue #193)의 sourceEventType. 원본 Domain Event Class
        // 이름을 그대로 쓴다(InquiryAnsweredNotificationListener와 같은 이유).
        const val SOURCE_EVENT_TYPE = "ProgramApplicationAppliedEvent"
    }
}
