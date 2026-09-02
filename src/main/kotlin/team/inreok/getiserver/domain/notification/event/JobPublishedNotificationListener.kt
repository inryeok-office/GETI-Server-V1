package team.inreok.getiserver.domain.notification.event

import org.slf4j.LoggerFactory
import org.springframework.core.task.TaskExecutor
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import team.inreok.getiserver.domain.job.event.JobDiscordAction
import team.inreok.getiserver.domain.job.event.JobDiscordEvent
import team.inreok.getiserver.domain.job.query.JobDiscordPayloadQueryPort
import team.inreok.getiserver.domain.member.query.NotificationAudienceQueryPort
import team.inreok.getiserver.domain.notification.dto.NotificationCreateCommand
import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetType
import team.inreok.getiserver.domain.notification.entity.type.NotificationType
import team.inreok.getiserver.domain.notification.service.NotificationService

/**
 * Job이 발행하는 [JobDiscordEvent]를 받아 `PUBLISHED`일 때만 대상 학년 재학생에게
 * `JOB_PUBLISHED` 인앱 알림을 만든다(Issue #191, 제품 계약 확정). 이미 Discord 연동에 쓰이는
 * 이 Event를 그대로 구독한다 -- 새 Domain Event를 추가하지 않고 기존 계약을 재사용한다
 * ([JobDiscordEventListener]와 나란히 같은 Event를 구독하는 두 번째 Listener).
 *
 * 수신자는 Recommendation 점수가 아니라 기존 공고 지원 자격 판정
 * (`JobApplicationEligibility.computeEligibilityReason`)이 쓰는 조건과 동일하게 재학 상태 +
 * 대상 학년으로 결정한다([NotificationAudienceQueryPort] 참고). 대상 학년이 없으면(`targetGrade
 * == null`) 전 학년 재학생이 대상이다.
 *
 * 수신자 수만큼 Insert가 이어지므로 실제 알림 생성은 전용 `notificationTaskExecutor`로 넘긴다
 * ([ProgramDeletedNotificationListener]와 동일한 이유·방식).
 */
@Component
class JobPublishedNotificationListener(
    private val jobDiscordPayloadQueryPort: JobDiscordPayloadQueryPort,
    private val notificationAudienceQueryPort: NotificationAudienceQueryPort,
    private val notificationService: NotificationService,
    private val notificationTaskExecutor: TaskExecutor,
) {
    private val log = LoggerFactory.getLogger(JobPublishedNotificationListener::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onJobDiscordEvent(event: JobDiscordEvent) {
        if (event.action != JobDiscordAction.PUBLISHED) return
        notificationTaskExecutor.execute { createNotifications(event) }
    }

    // 대상을 찾지 못하거나 조회가 실패하면 그 시점에 바로 끝내는 편이 중첩 없이 읽기 쉬워
    // ProgramDeletedNotificationListener와 같은 이유로 Early Return을 유지하고 detekt 기본
    // ReturnCount 한도(2)를 초과하는 부분만 Suppress한다(JobApplicationEligibility.kt와 동일한
    // 전례).
    @Suppress("ReturnCount")
    private fun createNotifications(event: JobDiscordEvent) {
        val snapshot =
            runCatching { jobDiscordPayloadQueryPort.findById(event.jobId) }
                .getOrElse { ex ->
                    log.error("게시된 공고 조회 실패(jobId={})", event.jobId, ex)
                    return
                }
        if (snapshot == null) {
            log.warn("JOB_PUBLISHED 알림을 생성하지 않습니다: 대상을 찾을 수 없습니다(jobId={})", event.jobId)
            return
        }

        val targetGrades = setOfNotNull(snapshot.targetGrade)
        val recipientMemberIds =
            runCatching { notificationAudienceQueryPort.findEligibleStudentIds(targetGrades) }
                .getOrElse { ex ->
                    log.error("게시 알림 대상 학생 조회 실패(jobId={})", event.jobId, ex)
                    return
                }

        // 한 수신자의 알림 저장이 실패해도 나머지 수신자는 알림을 받아야 하므로 개별로 감싼다
        // (ProgramDeletedNotificationListener와 동일한 이유).
        recipientMemberIds.distinct().forEach { memberId ->
            runCatching { createNotification(event.jobId, snapshot.title, memberId) }
                .onFailure { ex ->
                    log.error("공고 게시 알림 생성 중 처리되지 않은 오류(jobId={}, memberId={})", event.jobId, memberId, ex)
                }
        }
    }

    private fun createNotification(
        jobId: Long,
        title: String,
        recipientMemberId: Long,
    ) {
        notificationService.create(
            NotificationCreateCommand(
                recipientMemberId = recipientMemberId,
                type = NotificationType.JOB_PUBLISHED,
                title = "새 채용 공고가 게시되었습니다",
                content = "\"$title\" 공고가 게시되었습니다.",
                sourceEventType = SOURCE_EVENT_TYPE,
                sourceEventId = jobId,
                targetType = NotificationTargetType.JOB,
                targetId = jobId,
            ),
        )
    }

    private companion object {
        // Notification Idempotency Identity(Issue #193)의 sourceEventType. JobDiscordEvent 하나가
        // PUBLISHED/UPDATED/CLOSED/DELETED 여러 Action을 나르므로, Action을 값에 포함해
        // JOB_CLOSED(JobClosedNotificationListener)와 같은 jobId를 써도 서로 다른 Idempotency
        // Identity를 갖게 한다 -- 그렇지 않으면 두 알림이 recipientMemberId + sourceEventType +
        // sourceEventId 조합에서 충돌해 두 번째 알림이 "이미 존재함"으로 조용히 사라진다.
        const val SOURCE_EVENT_TYPE = "JobDiscordEvent:PUBLISHED"
    }
}
