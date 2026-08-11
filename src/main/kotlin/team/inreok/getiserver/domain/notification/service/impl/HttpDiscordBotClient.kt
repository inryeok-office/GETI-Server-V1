package team.inreok.getiserver.domain.notification.service.impl

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.body
import team.inreok.getiserver.domain.notification.config.DiscordBotHttpConfig
import team.inreok.getiserver.domain.notification.config.DiscordBotProperties
import team.inreok.getiserver.domain.notification.service.DiscordBotClient
import team.inreok.getiserver.domain.notification.service.DiscordBotResult
import team.inreok.getiserver.domain.notification.service.DiscordCreateCommand
import team.inreok.getiserver.domain.notification.service.DiscordPatchCommand

/**
 * GETI-Bot-V1 Internal REST API를 호출하는 Adapter다.
 *
 * 새 HTTP Library를 추가하지 않고 저장소가 이미 쓰는 [RestClient]를 사용한다(후속 요구사항
 * 문서 §15, `DiscordWebhookClient`·`DgOAuthProviderClient`와 같은 방식).
 *
 * ## Timeout (§42)
 *
 * Bot은 자체적으로 10초 Command Timeout을 걸고 초과 시 `DISCORD_UNAVAILABLE`(`retryable=true`)로
 * 응답한다. 그래서 read timeout을 그보다 **길게** 잡는다 -- 짧게 잡으면 Bot이 실제로는 Discord에
 * 게시했는데 서버만 실패로 판단하는 구간이 생기고, Bot의 Idempotency는 InMemory라 Bot 재시작
 * 시 사라져 그 구간을 막아주지 못한다(§38).
 *
 * ## 로그 (§41)
 *
 * Internal API Key, 전체 Request Payload, 응답 본문 원문을 남기지 않는다.
 */
@Component
class HttpDiscordBotClient(
    @param:Qualifier(DiscordBotHttpConfig.DISCORD_BOT_REST_CLIENT)
    private val restClient: RestClient,
    private val properties: DiscordBotProperties,
) : DiscordBotClient {
    override fun create(command: DiscordCreateCommand): DiscordBotResult =
        execute {
            restClient
                .post()
                .uri("${properties.baseUrl.trimEnd('/')}$MESSAGES_PATH")
                .header(HEADER_INTERNAL_API_KEY, properties.internalApiKey)
                .header(HEADER_REQUEST_ID, command.requestId)
                // Bot의 createHeadersSchema는 POST에만 적용된다. PATCH에는 붙이지 않는다.
                .header(HEADER_IDEMPOTENCY_KEY, command.idempotencyKey)
                .body(
                    mapOf(
                        "targetType" to command.targetType.name,
                        "action" to ACTION_CREATE,
                        "template" to command.template.name,
                        "channelId" to command.channelId,
                        "roleIds" to command.roleIds,
                        "data" to command.data,
                    ),
                ).retrieve()
                .body<Map<*, *>>()
        }

    override fun patch(command: DiscordPatchCommand): DiscordBotResult =
        execute {
            restClient
                .patch()
                .uri("${properties.baseUrl.trimEnd('/')}$MESSAGES_PATH/${command.messageId}")
                .header(HEADER_INTERNAL_API_KEY, properties.internalApiKey)
                .header(HEADER_REQUEST_ID, command.requestId)
                .body(
                    // roleIds를 넣지 않는다. Bot의 patchBodySchema는 .strict()이고 이 필드를
                    // 선언하지 않아, 빈 배열이라도 실으면 400 INVALID_REQUEST(재시도 불가)다.
                    mapOf(
                        "targetType" to command.targetType.name,
                        "action" to command.action.name,
                        "template" to command.template.name,
                        "channelId" to command.channelId,
                        "data" to command.data,
                    ),
                ).retrieve()
                .body<Map<*, *>>()
        }

    private fun execute(call: () -> Map<*, *>?): DiscordBotResult =
        try {
            val messageId = call()?.get("messageId") as? String
            if (messageId.isNullOrBlank()) {
                // 2xx인데 messageId가 없다. 호출부가 이를 Contract 오류로 처리한다(§21).
                DiscordBotResult.Failure(MALFORMED_RESPONSE, retryable = false, message = "Bot 응답에 messageId가 없습니다.")
            } else {
                DiscordBotResult.Success(messageId)
            }
        } catch (ex: RestClientResponseException) {
            toFailure(ex)
        } catch (ex: RestClientException) {
            // 연결 실패, Timeout, 응답 본문 해석 실패 등이다. Bot이 판단을 내려주지 못한
            // 상황이므로 서버가 재시도 가능으로 분류한다(§16·§42).
            log.warn("Discord Bot 호출 실패(네트워크 또는 응답 처리 오류)", ex)
            DiscordBotResult.Failure(BOT_UNREACHABLE, retryable = true, message = "Bot에 연결하지 못했습니다.")
        }

    /**
     * Bot Error Contract(`{ code, message, retryable, requestId }`)를 해석한다.
     *
     * **HTTP Status가 아니라 Body의 `code`를 우선한다.** Bot은 Route Handler에 도달하기 전
     * 오류(잘못된 JSON, Body 크기 초과 등)를 Fastify가 판단한 Status(예: 413)에 실어 보내므로,
     * Status만 보면 잘못 분류한다.
     */
    private fun toFailure(ex: RestClientResponseException): DiscordBotResult.Failure {
        val body =
            runCatching { ex.getResponseBodyAs(Map::class.java) }
                .getOrNull()
        val code = (body?.get("code") as? String)?.takeIf { it.isNotBlank() }
        val retryable = body?.get("retryable") as? Boolean

        if (code == null) {
            log.warn("Discord Bot이 해석할 수 없는 오류 응답을 반환했습니다(status={})", ex.statusCode.value())
            return DiscordBotResult.Failure(
                MALFORMED_RESPONSE,
                retryable = true,
                message = "Bot 오류 응답을 해석하지 못했습니다.",
            )
        }

        return DiscordBotResult.Failure(
            code = code,
            // Bot이 retryable을 명시하면 그 값을 쓰고, 없으면 알려진 코드의 기본값을 쓴다.
            // 둘 다 없으면 보수적으로 재시도하지 않는다 -- 원인을 모른 채 반복 호출하면 같은
            // 실패를 3번 되풀이할 뿐이다.
            retryable = retryable ?: DEFAULT_RETRYABLE_CODES.contains(code),
            message = (body["message"] as? String)?.take(MAX_ERROR_MESSAGE_LENGTH),
        )
    }

    private companion object {
        val log = LoggerFactory.getLogger(HttpDiscordBotClient::class.java)

        const val MESSAGES_PATH = "/internal/v1/discord/messages"
        const val ACTION_CREATE = "CREATE"

        const val HEADER_INTERNAL_API_KEY = "X-Internal-Api-Key"
        const val HEADER_REQUEST_ID = "X-Request-Id"
        const val HEADER_IDEMPOTENCY_KEY = "X-Idempotency-Key"

        const val MALFORMED_RESPONSE = "MALFORMED_RESPONSE"
        const val BOT_UNREACHABLE = "BOT_UNREACHABLE"

        const val MAX_ERROR_MESSAGE_LENGTH = 1000

        /**
         * GETI-Bot-V1 `src/internal-api/error.ts`의 `DEFAULT_RETRYABLE`에서 true인 코드다.
         * Bot이 `retryable`을 생략했을 때만 쓰는 보정값이다.
         */
        val DEFAULT_RETRYABLE_CODES = setOf("RATE_LIMITED", "DISCORD_UNAVAILABLE")
    }
}
