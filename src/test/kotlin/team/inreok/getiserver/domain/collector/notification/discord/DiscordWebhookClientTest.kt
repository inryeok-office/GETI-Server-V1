package team.inreok.getiserver.domain.collector.notification.discord

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

/**
 * 실제 Discord 서버 대신 Spring 표준 Test 도구인 MockRestServiceServer로 검증한다
 * (spring-boot-starter-webmvc-test에 이미 포함되어 있어 WireMock/MockWebServer를 새로 추가하지
 * 않는다). 실제 Discord Webhook으로 발송 Test를 하지 않았다(로컬에 실제 Webhook URL이 없다,
 * Issue #62 확장 범위 최종 보고 참고).
 */
class DiscordWebhookClientTest {
    private val restClientBuilder = RestClient.builder()
    private val mockServer = MockRestServiceServer.bindTo(restClientBuilder).build()
    private val client = DiscordWebhookClient(restClientBuilder)
    private val webhookUrl = "https://discord.com/api/webhooks/1/token"

    @Test
    fun `정상 발송이면 Sent와 Discord Message ID를 반환한다`() {
        mockServer
            .expect(requestTo("$webhookUrl?wait=true"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""{"id":"1234567890"}""", MediaType.APPLICATION_JSON))

        val result = client.send(webhookUrl, mapOf("content" to "test"))

        assertThat(result).isInstanceOf(DiscordWebhookSendResult.Sent::class.java)
        assertThat((result as DiscordWebhookSendResult.Sent).discordMessageId).isEqualTo("1234567890")
    }

    @Test
    fun `429는 Retry-After Header를 읽어 Retryable을 반환한다`() {
        mockServer
            .expect(requestTo("$webhookUrl?wait=true"))
            .andRespond(
                withStatus(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", "5")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"message":"rate limited","retry_after":5.0}"""),
            )

        val result = client.send(webhookUrl, mapOf("content" to "test"))

        assertThat(result).isInstanceOf(DiscordWebhookSendResult.Retryable::class.java)
        assertThat((result as DiscordWebhookSendResult.Retryable).retryAfterSeconds).isEqualTo(5L)
    }

    @Test
    fun `429 응답에 Retry-After Header가 없으면 본문의 retry_after를 사용한다`() {
        mockServer
            .expect(requestTo("$webhookUrl?wait=true"))
            .andRespond(
                withStatus(HttpStatus.TOO_MANY_REQUESTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"message":"rate limited","retry_after":2.5}"""),
            )

        val result = client.send(webhookUrl, mapOf("content" to "test")) as DiscordWebhookSendResult.Retryable

        assertThat(result.retryAfterSeconds).isEqualTo(2L)
    }

    @Test
    fun `잘못된 요청(400)은 NonRetryable을 반환한다`() {
        mockServer
            .expect(requestTo("$webhookUrl?wait=true"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST))

        val result = client.send(webhookUrl, mapOf("content" to "test"))

        assertThat(result).isInstanceOf(DiscordWebhookSendResult.NonRetryable::class.java)
    }

    @Test
    fun `Discord 서버 오류(5xx)는 Retryable을 반환한다`() {
        mockServer
            .expect(requestTo("$webhookUrl?wait=true"))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE))

        val result = client.send(webhookUrl, mapOf("content" to "test"))

        assertThat(result).isInstanceOf(DiscordWebhookSendResult.Retryable::class.java)
    }
}
