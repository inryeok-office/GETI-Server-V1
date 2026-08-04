package team.inreok.getiserver.domain.collector.notification.discord

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/** Discord Webhook 발송 결과. Payload·응답 원문은 담지 않는다(민감정보·본문 노출 금지). */
sealed class DiscordWebhookSendResult {
    data class Sent(
        val discordMessageId: String?,
    ) : DiscordWebhookSendResult()

    data class Retryable(
        val reason: String,
        val retryAfterSeconds: Long? = null,
    ) : DiscordWebhookSendResult()

    data class NonRetryable(
        val reason: String,
    ) : DiscordWebhookSendResult()
}

/**
 * Discord Webhook으로 Embed Payload를 전송하는 순수 HTTP Client다. Webhook URL은 호출 시점에만
 * 사용하고 저장하지 않는다. `?wait=true`로 요청해 성공 시 생성된 Message의 id를 돌려받는다
 * (docs/development/cd.md의 기존 Discord CD 알림과 동일한 관례). 429는 `Retry-After` Header와
 * 응답 본문의 `retry_after`를 함께 확인해 재시도 대기시간을 계산한다.
 */
@Component
class DiscordWebhookClient(
    restClientBuilder: RestClient.Builder,
) {
    private val restClient = restClientBuilder.build()

    fun send(
        webhookUrl: String,
        payload: Map<String, Any?>,
    ): DiscordWebhookSendResult =
        try {
            val body =
                restClient
                    .post()
                    .uri("$webhookUrl?wait=true")
                    .body(payload)
                    .retrieve()
                    .body<Map<*, *>>()
            DiscordWebhookSendResult.Sent(discordMessageId = body?.get("id") as? String)
        } catch (ex: HttpClientErrorException) {
            toClientErrorResult(ex)
        } catch (ex: HttpServerErrorException) {
            DiscordWebhookSendResult.Retryable("Discord가 서버 오류를 반환했습니다(${ex.statusCode.value()}).")
        } catch (ex: java.net.SocketTimeoutException) {
            log.warn("Discord Webhook 응답 시간 초과", ex)
            DiscordWebhookSendResult.Retryable("Discord 응답이 시간 내에 도착하지 않았습니다.")
        } catch (ex: ResourceAccessException) {
            log.warn("Discord Webhook 호출 중 네트워크 오류", ex)
            DiscordWebhookSendResult.Retryable("Discord 호출 중 네트워크 오류가 발생했습니다.")
        }

    private fun toClientErrorResult(ex: HttpClientErrorException): DiscordWebhookSendResult =
        if (ex.statusCode.value() == HTTP_TOO_MANY_REQUESTS) {
            DiscordWebhookSendResult.Retryable(
                "Discord 호출 제한을 초과했습니다(429).",
                retryAfterSeconds(ex),
            )
        } else {
            DiscordWebhookSendResult.NonRetryable("Discord가 잘못된 요청 오류를 반환했습니다(${ex.statusCode.value()}).")
        }

    // Retry-After Header가 없으면 응답 본문의 retry_after(Discord Rate Limit 표준 필드)를
    // 확인한다. 둘 다 없으면 null(호출 측이 자체 기본 재시도 간격을 사용한다).
    private fun retryAfterSeconds(ex: HttpClientErrorException): Long? {
        val headerValue = ex.responseHeaders?.getFirst("Retry-After")?.toDoubleOrNull()
        val bodyValue =
            RETRY_AFTER_BODY_PATTERN
                .find(ex.responseBodyAsString)
                ?.groupValues
                ?.get(1)
                ?.toDoubleOrNull()
        val seconds = headerValue ?: bodyValue ?: return null
        return seconds.toLong().coerceIn(1, MAX_RETRY_AFTER_SECONDS)
    }

    private companion object {
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val MAX_RETRY_AFTER_SECONDS = 3_600L
        val RETRY_AFTER_BODY_PATTERN = Regex("\"retry_after\"\\s*:\\s*([0-9.]+)")
        val log = LoggerFactory.getLogger(DiscordWebhookClient::class.java)
    }
}
