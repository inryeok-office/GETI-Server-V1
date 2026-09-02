package team.inreok.getiserver.domain.job.event

import org.springframework.modulith.NamedInterface

/**
 * Discord 알림이 필요할 정도로 Job이 바뀌었음을 알린다(Notification 후속 요구사항 문서 §26,
 * `docs/notification/discord-event-wiring-plan.md` §4). 기존 [JobChangedEvent]를 재사용하지
 * 않는 이유는 그 Event가 `jobId`만 담아 CREATE/UPDATE/CLOSE_NOTICE/DELETE_NOTICE 중 무엇을
 * Discord에 보내야 하는지 구분할 수 없기 때문이다(`search`의 재조회 방식과 Discord의
 * Action 기반 API 계약은 요구하는 정보가 다르다). `search`가 이미 구독 중인 [JobChangedEvent]의
 * 계약은 그대로 두고, 이 Event는 `JobServiceImpl`이 **함께** 발행한다.
 *
 * `Entity`를 담지 않고 `jobId`+[JobDiscordAction]만 담는다 — 구독 측(`notification`)이 공개
 * Query Port로 최신 상태를 다시 읽는다(저장소 전체 관례).
 */
@NamedInterface
data class JobDiscordEvent(
    val jobId: Long,
    val action: JobDiscordAction,
)
