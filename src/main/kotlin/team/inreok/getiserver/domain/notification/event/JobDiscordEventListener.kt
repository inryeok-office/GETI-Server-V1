package team.inreok.getiserver.domain.notification.event

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import team.inreok.getiserver.domain.job.event.JobDiscordAction
import team.inreok.getiserver.domain.job.event.JobDiscordEvent
import team.inreok.getiserver.domain.job.query.JobDiscordPayloadQueryPort
import team.inreok.getiserver.domain.notification.dto.DiscordDeliveryEnqueueCommand
import team.inreok.getiserver.domain.notification.entity.type.DiscordMessageTemplate
import team.inreok.getiserver.domain.notification.service.DiscordDeliveryService
import team.inreok.getiserver.global.discord.DiscordChannelResolver

/**
 * Job이 발행하는 [JobDiscordEvent]를 받아 Discord Delivery를 예약한다(Notification 후속 요구사항
 * 문서 §26, `docs/notification/discord-event-wiring-plan.md` §4.3). Commit 이후에만 실행되어
 * (`AFTER_COMMIT`) 아직 반영되지 않은 값을 예약하지 않는다 -- [JobIndexSyncEventListener]와
 * 동일한 방식과 이유다.
 *
 * 이 Listener가 실패해도(대상 조회 실패 등) 이미 Commit된 공고 등록·수정 Transaction에는 영향을
 * 주지 않는다 -- `AFTER_COMMIT` 시점에는 원본 Transaction이 이미 끝났기 때문이다.
 */
@Component
class JobDiscordEventListener(
    private val jobDiscordPayloadQueryPort: JobDiscordPayloadQueryPort,
    private val discordChannelResolver: DiscordChannelResolver,
    private val discordDeliveryService: DiscordDeliveryService,
) {
    private val log = LoggerFactory.getLogger(JobDiscordEventListener::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onJobDiscordEvent(event: JobDiscordEvent) {
        runCatching { process(event) }
            .onFailure { ex ->
                log.error("Job Discord Delivery 예약 중 처리되지 않은 오류(jobId={}, action={})", event.jobId, event.action, ex)
            }
    }

    private fun process(event: JobDiscordEvent) {
        val snapshot = jobDiscordPayloadQueryPort.findById(event.jobId)
        if (snapshot == null) {
            log.warn("Discord Delivery를 생성하지 않습니다: 대상을 찾을 수 없습니다(jobId={})", event.jobId)
            return
        }
        val channelId = discordChannelResolver.resolveJobChannelId(snapshot.discordChannelKey)
        if (channelId == null) {
            // 허용 채널이 아직 설정되지 않았다(§5.1). Fail-Fast가 아니다 -- 값이 채워지면
            // 재배포 없이 다음 등록·수정부터 정상 예약된다.
            log.warn("Discord 채널이 설정되지 않아 Delivery를 생성하지 않습니다(jobId={}, action={})", event.jobId, event.action)
            return
        }

        discordDeliveryService.enqueue(
            DiscordDeliveryEnqueueCommand(
                template = templateFor(event.action),
                targetId = event.jobId,
                channelId = channelId,
                // Mention은 최초 성공 CREATE 한 번만 허용된다(§24). CREATE가 아닌 Action은
                // enqueue()가 어차피 저장하지 않지만, 여기서도 계산 자체를 하지 않는다.
                roleIds =
                    if (event.action == JobDiscordAction.PUBLISHED) {
                        discordChannelResolver.roleIdsForGrades(listOfNotNull(snapshot.targetGrade))
                    } else {
                        emptyList()
                    },
                sourceUpdatedAt = if (event.action == JobDiscordAction.UPDATED) snapshot.updatedAt else null,
            ),
        )
    }

    private fun templateFor(action: JobDiscordAction): DiscordMessageTemplate =
        when (action) {
            JobDiscordAction.PUBLISHED -> DiscordMessageTemplate.JOB_PUBLISHED
            JobDiscordAction.UPDATED -> DiscordMessageTemplate.JOB_UPDATED
            JobDiscordAction.CLOSED -> DiscordMessageTemplate.JOB_CLOSED
            JobDiscordAction.DELETED -> DiscordMessageTemplate.JOB_DELETED
        }
}
