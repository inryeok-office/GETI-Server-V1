package team.inreok.getiserver.domain.program.event

import org.springframework.modulith.NamedInterface

/**
 * [ProgramDiscordEvent]가 알리는 변경 종류다. `CLOSED`는 `ProgramCloseScheduler`가 신청 종료
 * 시각이 지난 PUBLISHED Program을 CLOSED로 전이할 때만 발행한다(`ProgramCloseServiceImpl`,
 * Issue #120). `ProgramServiceImpl.changeStatus()`가 참조하는 `allowedTransitions()`는 여전히
 * `PUBLISHED -> CLOSED`를 허용하지 않아 API로는 이 Action이 발행되지 않는다
 * (`docs/notification/discord-event-wiring-plan.md` §2/§9 참고, 최초 결정은 Issue #97 당시
 * Scheduler 부재로 연결을 보류한 것이었고 Issue #120에서 연결을 완료했다).
 */
@NamedInterface
enum class ProgramDiscordAction {
    PUBLISHED,
    UPDATED,
    CLOSED,
    DELETED,
}
