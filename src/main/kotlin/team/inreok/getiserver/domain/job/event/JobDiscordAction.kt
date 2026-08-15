package team.inreok.getiserver.domain.job.event

import org.springframework.modulith.NamedInterface

/**
 * [JobDiscordEvent]가 알리는 변경 종류다. `domain.notification`의 `DiscordMessageTemplate`을
 * 여기서 직접 쓰지 않는다 -- `job`이 `notification`의 타입을 알게 되면 `job → notification`
 * 역방향 의존이 생겨 순환이 된다(`notification`은 이미 `job.query`를 소비 중이다). Template
 * 매핑은 구독 측(`notification`)이 한다.
 */
@NamedInterface
enum class JobDiscordAction {
    PUBLISHED,
    UPDATED,
    CLOSED,
    DELETED,
}
