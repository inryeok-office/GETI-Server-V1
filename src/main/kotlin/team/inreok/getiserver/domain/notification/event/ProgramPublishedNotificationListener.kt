package team.inreok.getiserver.domain.notification.event

import org.slf4j.LoggerFactory
import org.springframework.core.task.TaskExecutor
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import team.inreok.getiserver.domain.member.query.NotificationAudienceQueryPort
import team.inreok.getiserver.domain.notification.dto.NotificationCreateCommand
import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetType
import team.inreok.getiserver.domain.notification.entity.type.NotificationType
import team.inreok.getiserver.domain.notification.service.NotificationService
import team.inreok.getiserver.domain.program.event.ProgramDiscordAction
import team.inreok.getiserver.domain.program.event.ProgramDiscordEvent
import team.inreok.getiserver.domain.program.query.ProgramDiscordPayloadQueryPort

/**
 * Program이 발행하는 [ProgramDiscordEvent]를 받아 `PUBLISHED`일 때만 대상 학년 재학생에게
 * `PROGRAM_PUBLISHED` 인앱 알림을 만든다(Issue #191, 제품 계약 확정). 이미 Discord 연동에 쓰이는
 * 이 Event를 그대로 구독한다 -- [JobPublishedNotificationListener]와 같은 이유로 새 Domain
 * Event를 추가하지 않는다.
 *
 * 수신자는 기존 Program 신청 자격 판정(`ProgramEligibility.computeProgramEligibilityReason`)이
 * 쓰는 조건과 동일하게 재학 상태 + 대상 학년으로 결정한다. Program은 Job과 달리 대상 학년이
 * 여러 개일 수 있어(`program_target_grades`) [ProgramDiscordPayloadQueryPort]의
 * `targetGrades: List<Int>`를 그대로 [NotificationAudienceQueryPort]에 넘긴다.
 */
@Component
class ProgramPublishedNotificationListener(
    private val programDiscordPayloadQueryPort: ProgramDiscordPayloadQueryPort,
    private val notificationAudienceQueryPort: NotificationAudienceQueryPort,
    private val notificationService: NotificationService,
    private val notificationTaskExecutor: TaskExecutor,
) {
    private val log = LoggerFactory.getLogger(ProgramPublishedNotificationListener::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onProgramDiscordEvent(event: ProgramDiscordEvent) {
        if (event.action != ProgramDiscordAction.PUBLISHED) return
        notificationTaskExecutor.execute { createNotifications(event) }
    }

    // JobPublishedNotificationListener와 같은 이유로 Early Return 3개에 대해서만 detekt
    // ReturnCount 한도(2)를 Suppress한다.
    @Suppress("ReturnCount")
    private fun createNotifications(event: ProgramDiscordEvent) {
        val snapshot =
            runCatching { programDiscordPayloadQueryPort.findById(event.programId) }
                .getOrElse { ex ->
                    log.error("게시된 프로그램 조회 실패(programId={})", event.programId, ex)
                    return
                }
        if (snapshot == null) {
            log.warn("PROGRAM_PUBLISHED 알림을 생성하지 않습니다: 대상을 찾을 수 없습니다(programId={})", event.programId)
            return
        }

        val recipientMemberIds =
            runCatching { notificationAudienceQueryPort.findEligibleStudentIds(snapshot.targetGrades.toSet()) }
                .getOrElse { ex ->
                    log.error("게시 알림 대상 학생 조회 실패(programId={})", event.programId, ex)
                    return
                }

        recipientMemberIds.distinct().forEach { memberId ->
            runCatching { createNotification(event.programId, snapshot.title, memberId) }
                .onFailure { ex ->
                    log.error(
                        "프로그램 게시 알림 생성 중 처리되지 않은 오류(programId={}, memberId={})",
                        event.programId,
                        memberId,
                        ex,
                    )
                }
        }
    }

    private fun createNotification(
        programId: Long,
        title: String,
        recipientMemberId: Long,
    ) {
        notificationService.create(
            NotificationCreateCommand(
                recipientMemberId = recipientMemberId,
                type = NotificationType.PROGRAM_PUBLISHED,
                title = "새 프로그램이 게시되었습니다",
                content = "\"$title\" 프로그램이 게시되었습니다.",
                sourceEventType = SOURCE_EVENT_TYPE,
                sourceEventId = programId,
                targetType = NotificationTargetType.PROGRAM,
                targetId = programId,
            ),
        )
    }

    private companion object {
        // JobPublishedNotificationListener와 같은 이유로 Action을 값에 포함한다(ProgramDiscordEvent
        // 하나가 PUBLISHED/UPDATED/CLOSED/DELETED 여러 Action을 나른다).
        const val SOURCE_EVENT_TYPE = "ProgramDiscordEvent:PUBLISHED"
    }
}
