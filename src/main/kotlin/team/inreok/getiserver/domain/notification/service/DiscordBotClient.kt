package team.inreok.getiserver.domain.notification.service

import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryAction
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryTargetType
import team.inreok.getiserver.domain.notification.entity.type.DiscordMessageTemplate

/**
 * Notification 도메인이 소유하는 Outbound Port다(후속 요구사항 문서 §14). 실제 전송은
 * `domain.notification.service.impl.HttpDiscordBotClient`가 GETI-Bot-V1 Internal REST API로
 * 수행하고, Test는 Fake 구현으로 대체한다(§44).
 *
 * **Spring HTTP Client 타입을 계약에 노출하지 않는다** -- `ResponseEntity`,
 * `RestClient.ResponseSpec`, `WebClient.ResponseSpec`은 이 파일에 등장하지 않는다.
 *
 * 서버는 Discord Embed를 만들지 않는다(§3). Template 이름과 의미 데이터만 넘기고 렌더링은
 * 전부 Bot이 한다. 그래서 이 Port에도 Embed/Color/Field 개념이 없다.
 */
interface DiscordBotClient {
    /** `POST /internal/v1/discord/messages`. 성공하면 새 Discord 메시지의 id를 돌려준다. */
    fun create(command: DiscordCreateCommand): DiscordBotResult

    /** `PATCH /internal/v1/discord/messages/{messageId}`. 기존 메시지를 수정한다. */
    fun patch(command: DiscordPatchCommand): DiscordBotResult
}

/**
 * 새 Discord 메시지 게시 요청이다.
 *
 * [roleIds]는 **CREATE에만 존재한다**. Mention은 논리적 메시지 생애주기에서 최초 성공 CREATE
 * 한 번만 허용되기 때문이다(§24). 첫 CREATE가 아직 성공하지 않았다면 재시도에서도 Mention할
 * 수 있으므로, "재시도면 항상 Mention 금지"가 아니라 "최초 성공한 CREATE에 최대 1회"가 정확한
 * 규칙이다.
 */
data class DiscordCreateCommand(
    val targetType: DiscordDeliveryTargetType,
    val template: DiscordMessageTemplate,
    val channelId: String,
    val roleIds: List<String>,
    val data: Map<String, String>,
    val requestId: String,
    val idempotencyKey: String,
)

/**
 * 기존 Discord 메시지 수정 요청이다(UPDATE / CLOSE_NOTICE / DELETE_NOTICE).
 *
 * **`roleIds`가 의도적으로 없다.** GETI-Bot-V1의 PATCH Body Schema는 `.strict()`이고 `roleIds`
 * 필드를 선언하지 않아, 빈 배열이라도 실어 보내면 `400 INVALID_REQUEST`(재시도 불가)로 거절된다.
 * 후속 요구사항 문서 §24는 "PATCH에는 mentionRoleIds를 비워서 전달"이라고 적었지만 실제 계약이
 * 다르므로 계약을 따른다. 타입 자체에 필드를 두지 않아 실수로도 보낼 수 없게 했다.
 *
 * [idempotencyKey]도 없다 -- Bot의 `createHeadersSchema`는 POST에만 적용되어 PATCH는
 * `X-Idempotency-Key`를 요구하지 않는다.
 */
data class DiscordPatchCommand(
    val targetType: DiscordDeliveryTargetType,
    val action: DiscordDeliveryAction,
    val template: DiscordMessageTemplate,
    val channelId: String,
    val messageId: String,
    val data: Map<String, String>,
    val requestId: String,
)

/**
 * Bot 호출 결과다.
 *
 * 실패를 예외로 던지지 않고 값으로 돌려준다 -- Worker가 [Failure.retryable]을 보고 다음 상태를
 * 계산하는 것이 정상 흐름이라, 예외로 만들면 호출부가 `catch` 안에서 상태 전이를 하게 되어
 * 성공 경로와 뒤섞이기 때문이다.
 */
sealed interface DiscordBotResult {
    data class Success(
        val messageId: String,
    ) : DiscordBotResult

    /**
     * @param code Bot Error Contract의 `code`(`INVALID_REQUEST`, `RATE_LIMITED` 등) 또는 서버가
     *   자체적으로 분류한 코드(`BOT_UNREACHABLE`, `MALFORMED_RESPONSE`).
     * @param retryable 재시도로 해결될 수 있는 실패인지. Bot의 값을 참고하되 최종 판단은
     *   서버가 한다(§16).
     * @param message 운영자가 원인을 파악할 최소한의 문구. Stack Trace나 Discord SDK Error를
     *   담지 않는다.
     */
    data class Failure(
        val code: String,
        val retryable: Boolean,
        val message: String?,
    ) : DiscordBotResult
}
