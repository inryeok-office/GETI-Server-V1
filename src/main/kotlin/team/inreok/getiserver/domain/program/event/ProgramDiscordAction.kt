package team.inreok.getiserver.domain.program.event

import org.springframework.modulith.NamedInterface

/**
 * [ProgramDiscordEvent]가 알리는 변경 종류다. `PROGRAM_CLOSED`에 대응하는 값을 두지 않는다 --
 * `ProgramStatus.CLOSED`에 도달하는 코드가 저장소에 없어(신청 종료 시각 도달 시 서버 Scheduler가
 * 처리한다고 문서화만 되어 있고 그 Scheduler가 미구현) 발동할 수 없는 Action을 만들지 않는다
 * (`docs/notification/discord-event-wiring-plan.md` §2). Program 자동 마감 Scheduler가 생기면
 * 그때 추가한다.
 */
@NamedInterface
enum class ProgramDiscordAction {
    PUBLISHED,
    UPDATED,
    DELETED,
}
