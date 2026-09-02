package team.inreok.getiserver.domain.program.event

import org.springframework.modulith.NamedInterface

/**
 * Discord 알림이 필요할 정도로 Program이 바뀌었음을 알린다(Notification 후속 요구사항 문서
 * §27, `docs/notification/discord-event-wiring-plan.md` §4). Program은 기존에 어떤 Domain
 * Event도 발행하지 않아 새로 만든다.
 *
 * `Entity`를 담지 않고 `programId`+[ProgramDiscordAction]만 담는다 -- `JobDiscordEvent`와 같은
 * 이유다.
 */
@NamedInterface
data class ProgramDiscordEvent(
    val programId: Long,
    val action: ProgramDiscordAction,
)
