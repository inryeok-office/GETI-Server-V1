package team.inreok.getiserver.domain.notification.event

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import team.inreok.getiserver.domain.notification.dto.DiscordDeliveryEnqueueCommand
import team.inreok.getiserver.domain.notification.entity.type.DiscordMessageTemplate
import team.inreok.getiserver.domain.notification.service.DiscordDeliveryService
import team.inreok.getiserver.domain.program.event.ProgramDiscordAction
import team.inreok.getiserver.domain.program.event.ProgramDiscordEvent
import team.inreok.getiserver.domain.program.query.ProgramDiscordPayloadQueryPort
import team.inreok.getiserver.global.discord.DiscordChannelResolver

/**
 * Program이 발행하는 [ProgramDiscordEvent]를 받아 Discord Delivery를 예약한다(Notification
 * 후속 요구사항 문서 §27, `docs/notification/discord-event-wiring-plan.md` §4.3). 방향과 이유는
 * [JobDiscordEventListener]와 같다.
 */
@Component
class ProgramDiscordEventListener(
    private val programDiscordPayloadQueryPort: ProgramDiscordPayloadQueryPort,
    private val discordChannelResolver: DiscordChannelResolver,
    private val discordDeliveryService: DiscordDeliveryService,
) {
    private val log = LoggerFactory.getLogger(ProgramDiscordEventListener::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onProgramDiscordEvent(event: ProgramDiscordEvent) {
        runCatching { process(event) }
            .onFailure { ex ->
                log.error(
                    "Program Discord Delivery 예약 중 처리되지 않은 오류(programId={}, action={})",
                    event.programId,
                    event.action,
                    ex,
                )
            }
    }

    private fun process(event: ProgramDiscordEvent) {
        val snapshot = programDiscordPayloadQueryPort.findById(event.programId)
        if (snapshot == null) {
            log.warn("Discord Delivery를 생성하지 않습니다: 대상을 찾을 수 없습니다(programId={})", event.programId)
            return
        }
        // Program은 등록 시점에 이미 원시 Snowflake를 저장한다(§6 결정 6). 허용 목록 검증도
        // 등록 시점에 끝났으므로 여기서는 값을 정규화만 한다.
        val channelId = discordChannelResolver.resolveProgramChannelId(snapshot.discordChannelId)
        if (channelId == null) {
            log.warn(
                "Discord 채널이 설정되지 않아 Delivery를 생성하지 않습니다(programId={}, action={})",
                event.programId,
                event.action,
            )
            return
        }

        discordDeliveryService.enqueue(
            DiscordDeliveryEnqueueCommand(
                template = templateFor(event.action),
                targetId = event.programId,
                channelId = channelId,
                roleIds =
                    if (event.action == ProgramDiscordAction.PUBLISHED) {
                        discordChannelResolver.roleIdsForGrades(snapshot.targetGrades)
                    } else {
                        emptyList()
                    },
                sourceUpdatedAt = if (event.action == ProgramDiscordAction.UPDATED) snapshot.updatedAt else null,
            ),
        )
    }

    private fun templateFor(action: ProgramDiscordAction): DiscordMessageTemplate =
        when (action) {
            ProgramDiscordAction.PUBLISHED -> DiscordMessageTemplate.PROGRAM_PUBLISHED
            ProgramDiscordAction.UPDATED -> DiscordMessageTemplate.PROGRAM_UPDATED
            ProgramDiscordAction.DELETED -> DiscordMessageTemplate.PROGRAM_DELETED
        }
}
