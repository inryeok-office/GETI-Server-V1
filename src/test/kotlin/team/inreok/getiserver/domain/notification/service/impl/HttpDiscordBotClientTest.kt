package team.inreok.getiserver.domain.notification.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import team.inreok.getiserver.domain.notification.config.DiscordBotProperties
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryAction
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryTargetType
import team.inreok.getiserver.domain.notification.entity.type.DiscordMessageTemplate
import team.inreok.getiserver.domain.notification.service.DiscordBotResult
import team.inreok.getiserver.domain.notification.service.DiscordCreateCommand
import team.inreok.getiserver.domain.notification.service.DiscordPatchCommand
import java.io.IOException

/**
 * GETI-Bot-V1 Internal API 계약을 고정하는 Contract Test다(후속 요구사항 문서 §45·§54).
 *
 * Bot 저장소(`src/internal-api/`, `src/renderer/`)의 실제 Schema·Header·ErrorCode를 그대로 기대값으로
 * 박아 두어, 두 저장소의 계약이 어긋나면 실제 연동 전에 이 Test가 먼저 깨지게 한다.
 *
 * 새 WireMock Dependency를 추가하지 않고 Spring이 이미 제공하는 [MockRestServiceServer]를 쓴다.
 */
class HttpDiscordBotClientTest {
    private val baseUrl = "http://geti-bot:3000"
    private val apiKey = "internal-api-key"
    private val createUrl = "$baseUrl/internal/v1/discord/messages"

    private lateinit var server: MockRestServiceServer
    private lateinit var client: HttpDiscordBotClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        client =
            HttpDiscordBotClient(
                restClient = builder.build(),
                properties =
                    DiscordBotProperties(enabled = true, baseUrl = baseUrl, internalApiKey = apiKey),
            )
    }

    // --- CREATE -----------------------------------------------------------

    @Test
    fun `CREATE는 POST로 Bot Endpoint를 호출한다`() {
        server
            .expect(requestTo(createUrl))
            .andExpect(method(org.springframework.http.HttpMethod.POST))
            .andRespond(created("""{"messageId":"m-1","requestId":"r-1"}"""))

        val result = client.create(createCommand())

        assertThat(result).isEqualTo(DiscordBotResult.Success("m-1"))
        server.verify()
    }

    @Test
    fun `CREATE는 인증 요청 추적 멱등 Header를 모두 보낸다`() {
        server
            .expect(requestTo(createUrl))
            .andExpect(header("X-Internal-Api-Key", apiKey))
            .andExpect(header("X-Request-Id", "req-1"))
            .andExpect(header("X-Idempotency-Key", "PROGRAM:100:CREATE"))
            .andRespond(created("""{"messageId":"m-1","requestId":"r-1"}"""))

        client.create(createCommand())

        server.verify()
    }

    @Test
    fun `CREATE Body는 Bot createBodySchema가 받는 필드만 담는다`() {
        server
            .expect(requestTo(createUrl))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.targetType").value("PROGRAM"))
            .andExpect(jsonPath("$.action").value("CREATE"))
            .andExpect(jsonPath("$.template").value("PROGRAM_PUBLISHED"))
            .andExpect(jsonPath("$.channelId").value("channel-1"))
            .andExpect(jsonPath("$.roleIds[0]").value("role-1"))
            .andExpect(jsonPath("$.data.programId").value("100"))
            .andRespond(created("""{"messageId":"m-1","requestId":"r-1"}"""))

        client.create(createCommand())

        server.verify()
    }

    // --- PATCH ------------------------------------------------------------

    @Test
    fun `PATCH는 messageId를 경로에 담아 호출한다`() {
        server
            .expect(requestTo("$createUrl/m-1"))
            .andExpect(method(org.springframework.http.HttpMethod.PATCH))
            .andRespond(withSuccess("""{"messageId":"m-1","requestId":"r-1"}""", MediaType.APPLICATION_JSON))

        val result = client.patch(patchCommand())

        assertThat(result).isEqualTo(DiscordBotResult.Success("m-1"))
        server.verify()
    }

    @Test
    fun `PATCH에는 Idempotency Header를 붙이지 않는다`() {
        // Bot의 createHeadersSchema는 POST에만 적용된다.
        server
            .expect(requestTo("$createUrl/m-1"))
            .andExpect(header("X-Internal-Api-Key", apiKey))
            .andExpect(headerDoesNotExist("X-Idempotency-Key"))
            .andRespond(withSuccess("""{"messageId":"m-1","requestId":"r-1"}""", MediaType.APPLICATION_JSON))

        client.patch(patchCommand())

        server.verify()
    }

    @Test
    fun `PATCH Body에 roleIds를 담지 않는다`() {
        // Bot의 patchBodySchema는 .strict()이고 roleIds를 선언하지 않아, 빈 배열이라도 실으면
        // 400 INVALID_REQUEST(재시도 불가)로 거절된다.
        server
            .expect(requestTo("$createUrl/m-1"))
            .andExpect(jsonPath("$.roleIds").doesNotExist())
            .andExpect(jsonPath("$.action").value("CLOSE_NOTICE"))
            .andExpect(jsonPath("$.template").value("PROGRAM_CLOSED"))
            .andRespond(withSuccess("""{"messageId":"m-1","requestId":"r-1"}""", MediaType.APPLICATION_JSON))

        client.patch(patchCommand())

        server.verify()
    }

    // --- 오류 응답 --------------------------------------------------------

    @Test
    fun `401은 재시도 불가 실패로 해석한다`() {
        respondWithError(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", retryable = false)

        val result = client.create(createCommand()) as DiscordBotResult.Failure

        assertThat(result.code).isEqualTo("UNAUTHORIZED")
        assertThat(result.retryable).isFalse()
    }

    @Test
    fun `400은 재시도 불가 실패로 해석한다`() {
        respondWithError(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", retryable = false)

        val result = client.create(createCommand()) as DiscordBotResult.Failure

        assertThat(result.code).isEqualTo("INVALID_REQUEST")
        assertThat(result.retryable).isFalse()
    }

    @Test
    fun `404 MESSAGE_NOT_FOUND는 재시도 불가 실패로 해석한다`() {
        respondWithError(HttpStatus.NOT_FOUND, "MESSAGE_NOT_FOUND", retryable = false)

        val result = client.create(createCommand()) as DiscordBotResult.Failure

        assertThat(result.code).isEqualTo("MESSAGE_NOT_FOUND")
        assertThat(result.retryable).isFalse()
    }

    @Test
    fun `429는 재시도 가능 실패로 해석한다`() {
        respondWithError(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", retryable = true)

        val result = client.create(createCommand()) as DiscordBotResult.Failure

        assertThat(result.code).isEqualTo("RATE_LIMITED")
        assertThat(result.retryable).isTrue()
    }

    @Test
    fun `503 DISCORD_UNAVAILABLE은 재시도 가능 실패로 해석한다`() {
        respondWithError(HttpStatus.SERVICE_UNAVAILABLE, "DISCORD_UNAVAILABLE", retryable = true)

        val result = client.create(createCommand()) as DiscordBotResult.Failure

        assertThat(result.retryable).isTrue()
    }

    @Test
    fun `retryable이 없으면 알려진 코드의 기본값으로 보정한다`() {
        server
            .expect(requestTo(createUrl))
            .andRespond(
                withStatus(HttpStatus.TOO_MANY_REQUESTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"code":"RATE_LIMITED","message":"slow down","requestId":"r-1"}"""),
            )

        val result = client.create(createCommand()) as DiscordBotResult.Failure

        assertThat(result.retryable).isTrue()
    }

    @Test
    fun `HTTP Status보다 Body의 code를 우선한다`() {
        // Bot은 Route Handler 도달 전 오류를 Fastify가 판단한 Status(예: 413)로 내려보낸다.
        server
            .expect(requestTo(createUrl))
            .andRespond(
                withStatus(HttpStatus.PAYLOAD_TOO_LARGE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"code":"INVALID_REQUEST","message":"too large","retryable":false,"requestId":"r"}"""),
            )

        val result = client.create(createCommand()) as DiscordBotResult.Failure

        assertThat(result.code).isEqualTo("INVALID_REQUEST")
        assertThat(result.retryable).isFalse()
    }

    @Test
    fun `해석할 수 없는 오류 응답은 재시도 가능으로 분류한다`() {
        server
            .expect(requestTo(createUrl))
            .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("<html>gateway</html>"))

        val result = client.create(createCommand()) as DiscordBotResult.Failure

        assertThat(result.code).isEqualTo("MALFORMED_RESPONSE")
        assertThat(result.retryable).isTrue()
    }

    @Test
    fun `messageId가 없는 성공 응답은 Contract 오류로 처리한다`() {
        // 요구사항 §21 -- CREATE가 성공했는데 messageId가 없으면 DELIVERED로 볼 수 없다.
        server
            .expect(requestTo(createUrl))
            .andRespond(created("""{"requestId":"r-1"}"""))

        val result = client.create(createCommand()) as DiscordBotResult.Failure

        assertThat(result.code).isEqualTo("MALFORMED_RESPONSE")
        assertThat(result.retryable).isFalse()
    }

    @Test
    fun `Bot에 연결하지 못하면 재시도 가능 실패로 분류한다`() {
        // Timeout도 같은 경로로 처리된다(요구사항 §42).
        server
            .expect(requestTo(createUrl))
            .andRespond(withException(IOException("connection refused")))

        val result = client.create(createCommand()) as DiscordBotResult.Failure

        assertThat(result.code).isEqualTo("BOT_UNREACHABLE")
        assertThat(result.retryable).isTrue()
    }

    @Test
    fun `오류 응답에 Internal API Key를 담아 돌려주지 않는다`() {
        respondWithError(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", retryable = false)

        val result = client.create(createCommand()) as DiscordBotResult.Failure

        assertThat(result.message).doesNotContain(apiKey)
    }

    // --- Helper -----------------------------------------------------------

    private fun created(body: String) =
        withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(body)

    private fun respondWithError(
        status: HttpStatus,
        code: String,
        retryable: Boolean,
    ) {
        server
            .expect(requestTo(createUrl))
            .andRespond(
                withStatus(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"code":"$code","message":"failed","retryable":$retryable,"requestId":"r-1"}"""),
            )
    }

    private fun createCommand() =
        DiscordCreateCommand(
            targetType = DiscordDeliveryTargetType.PROGRAM,
            template = DiscordMessageTemplate.PROGRAM_PUBLISHED,
            channelId = "channel-1",
            roleIds = listOf("role-1"),
            data = mapOf("programId" to "100", "title" to "여름 캠프"),
            requestId = "req-1",
            idempotencyKey = "PROGRAM:100:CREATE",
        )

    private fun patchCommand() =
        DiscordPatchCommand(
            targetType = DiscordDeliveryTargetType.PROGRAM,
            action = DiscordDeliveryAction.CLOSE_NOTICE,
            template = DiscordMessageTemplate.PROGRAM_CLOSED,
            channelId = "channel-1",
            messageId = "m-1",
            data = mapOf("programId" to "100", "title" to "여름 캠프"),
            requestId = "req-1",
        )
}
