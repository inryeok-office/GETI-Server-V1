package team.inreok.getiserver.domain.notification.event

import org.slf4j.LoggerFactory
import org.springframework.core.task.TaskExecutor
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import team.inreok.getiserver.domain.job.event.JobDiscordAction
import team.inreok.getiserver.domain.job.event.JobDiscordEvent
import team.inreok.getiserver.domain.job.query.JobDiscordPayloadQueryPort
import team.inreok.getiserver.domain.notification.dto.NotificationCreateCommand
import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetType
import team.inreok.getiserver.domain.notification.entity.type.NotificationType
import team.inreok.getiserver.domain.notification.service.NotificationService
import team.inreok.getiserver.domain.recommendation.query.JobBookmarkAudienceQueryPort

/**
 * Job이 발행하는 [JobDiscordEvent]를 받아 `CLOSED`일 때만 그 공고를 북마크한 회원에게
 * `JOB_CLOSED` 인앱 알림을 만든다(Issue #191, 제품 계약 확정 "대상 학년 전체가 아니라 실제 관심
 * 관계가 있는 사용자만"). [JobPublishedNotificationListener]와 같은 Event를 구독하지만 대상
 * Action과 수신자 결정 방식이 달라 별도 Listener로 둔다.
 *
 * 수신자 조회는 [JobBookmarkAudienceQueryPort]로 한다 -- Bookmark/관심 관계를 안정적으로 조회할
 * 수 있는지가 Issue #191의 구현 단계 확인 사항이었는데, `member_job_preferences`에 jobId 기준
 * 역방향 조회(`MemberJobPreferenceRepository.findMemberIdsByJobIdAndBookmarkedTrue`)가 이미
 * 가능해 BLOCKED로 분리하지 않고 정상 구현한다.
 */
@Component
class JobClosedNotificationListener(
    private val jobDiscordPayloadQueryPort: JobDiscordPayloadQueryPort,
    private val jobBookmarkAudienceQueryPort: JobBookmarkAudienceQueryPort,
    private val notificationService: NotificationService,
    private val notificationTaskExecutor: TaskExecutor,
) {
    private val log = LoggerFactory.getLogger(JobClosedNotificationListener::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onJobDiscordEvent(event: JobDiscordEvent) {
        if (event.action != JobDiscordAction.CLOSED) return
        notificationTaskExecutor.execute { createNotifications(event) }
    }

    // JobPublishedNotificationListener와 같은 이유로 Early Return 3개에 대해서만 detekt
    // ReturnCount 한도(2)를 Suppress한다.
    @Suppress("ReturnCount")
    private fun createNotifications(event: JobDiscordEvent) {
        val snapshot =
            runCatching { jobDiscordPayloadQueryPort.findById(event.jobId) }
                .getOrElse { ex ->
                    log.error("마감된 공고 조회 실패(jobId={})", event.jobId, ex)
                    return
                }
        if (snapshot == null) {
            log.warn("JOB_CLOSED 알림을 생성하지 않습니다: 대상을 찾을 수 없습니다(jobId={})", event.jobId)
            return
        }

        val recipientMemberIds =
            runCatching { jobBookmarkAudienceQueryPort.findBookmarkedMemberIds(event.jobId) }
                .getOrElse { ex ->
                    log.error("마감 알림 대상(북마크) 조회 실패(jobId={})", event.jobId, ex)
                    return
                }

        recipientMemberIds.distinct().forEach { memberId ->
            runCatching { createNotification(event.jobId, snapshot.title, memberId) }
                .onFailure { ex ->
                    log.error("공고 마감 알림 생성 중 처리되지 않은 오류(jobId={}, memberId={})", event.jobId, memberId, ex)
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
                type = NotificationType.JOB_CLOSED,
                title = "관심 등록한 공고가 마감되었습니다",
                content = "\"$title\" 공고가 마감되었습니다.",
                sourceEventType = SOURCE_EVENT_TYPE,
                sourceEventId = jobId,
                targetType = NotificationTargetType.JOB,
                targetId = jobId,
            ),
        )
    }

    private companion object {
        // JobPublishedNotificationListener와 같은 이유로 Action을 값에 포함한다.
        const val SOURCE_EVENT_TYPE = "JobDiscordEvent:CLOSED"
    }
}
