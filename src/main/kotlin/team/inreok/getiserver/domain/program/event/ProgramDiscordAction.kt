package team.inreok.getiserver.domain.program.event

import org.springframework.modulith.NamedInterface

/**
 * [ProgramDiscordEvent]가 알리는 변경 종류다. `PROGRAM_CLOSED`에 대응하는 값을 두지 않는다 --
 * `ProgramCloseScheduler`가 신청 종료 시각이 지난 PUBLISHED Program을 실제로 CLOSED로 전이하기
 * 시작했지만(`ProgramCloseServiceImpl`), Discord `PROGRAM_CLOSED` 연동은 별도 이슈로 남겨 이번
 * 범위에서 연결하지 않는다(`docs/notification/discord-event-wiring-plan.md` §2/§9, 사용자 확정).
 * Discord 연동 이슈가 진행되면 그때 추가한다.
 */
@NamedInterface
enum class ProgramDiscordAction {
    PUBLISHED,
    UPDATED,
    DELETED,
}
