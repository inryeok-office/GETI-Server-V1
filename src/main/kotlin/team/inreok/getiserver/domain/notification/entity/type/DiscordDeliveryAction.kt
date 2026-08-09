package team.inreok.getiserver.domain.notification.entity.type

/**
 * Discord 메시지에 수행할 논리적 작업이다(후속 요구사항 문서 §10).
 *
 * 재시도는 Action이 아니라 **기존 Action의 재수행**이다 — `RETRY` 값을 만들지 않는 이유다.
 *
 * [CREATE]만 GETI-Bot-V1의 `POST /internal/v1/discord/messages`로 가고, 나머지 셋은
 * `PATCH /internal/v1/discord/messages/{messageId}`로 간다. 세 PATCH Action은 모두 기존
 * 메시지를 **수정**할 뿐이며, Discord 메시지를 물리적으로 삭제하지 않는다(§22).
 */
enum class DiscordDeliveryAction {
    /** 새 Discord 메시지를 게시한다. Mention이 허용되는 유일한 Action이다(§24). */
    CREATE,

    /** 기존 메시지를 최신 내용으로 수정한다. */
    UPDATE,

    /** 기존 메시지를 마감 상태로 표시한다. */
    CLOSE_NOTICE,

    /** 기존 메시지를 삭제 안내 상태로 표시한다. 메시지 자체를 지우지 않는다. */
    DELETE_NOTICE,
}
