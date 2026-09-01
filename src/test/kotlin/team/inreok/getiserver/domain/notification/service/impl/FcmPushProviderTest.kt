package team.inreok.getiserver.domain.notification.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import team.inreok.getiserver.domain.notification.config.FcmProperties
import team.inreok.getiserver.domain.notification.entity.type.PushPlatform
import team.inreok.getiserver.domain.notification.service.PushSendCommand
import team.inreok.getiserver.domain.notification.service.PushSendResult
import tools.jackson.databind.ObjectMapper
import java.security.KeyPairGenerator
import java.util.Base64

/**
 * FCM HTTP v1 API 계약을 고정하는 Contract Test다(`HttpDiscordBotClientTest`와 같은 구조).
 * `MockRestServiceServer` 하나가 OAuth2 Token 발급과 FCM 전송 두 호출을 모두 가로챈다.
 */
class FcmPushProviderTest {
    private val objectMapper = ObjectMapper()
    private val projectId = "geti-app"
    private lateinit var server: MockRestServiceServer
    private lateinit var provider: FcmPushProvider

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        val restClient = builder.build()
        val properties = FcmProperties(enabled = true, serviceAccountKey = serviceAccountJson())
        val accessTokenProvider = FcmAccessTokenProvider(restClient, properties, objectMapper)
        provider = FcmPushProvider(restClient, properties, accessTokenProvider)
    }

    @Test
    fun `전송에 성공하면 메시지 이름을 돌려준다`() {
        server
            .expect(requestTo("https://oauth2.googleapis.com/token"))
            .andRespond(withSuccess("""{"access_token":"token-1","expires_in":3600}""", MediaType.APPLICATION_JSON))
        server
            .expect(requestTo("https://fcm.googleapis.com/v1/projects/$projectId/messages:send"))
            .andExpect(header("Authorization", "Bearer token-1"))
            .andRespond(
                withSuccess(
                    """{"name":"projects/$projectId/messages/msg-1"}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = provider.send(command())

        assertThat(result).isEqualTo(PushSendResult.Success("projects/$projectId/messages/msg-1"))
        server.verify()
    }

    @Test
    fun `요청 Body는 token title body data를 담는다`() {
        server
            .expect(requestTo("https://oauth2.googleapis.com/token"))
            .andRespond(withSuccess("""{"access_token":"token-1","expires_in":3600}""", MediaType.APPLICATION_JSON))
        server
            .expect(requestTo("https://fcm.googleapis.com/v1/projects/$projectId/messages:send"))
            .andExpect(jsonPath("$.message.token").value("device-token-1"))
            .andExpect(jsonPath("$.message.notification.title").value("제목"))
            .andExpect(jsonPath("$.message.notification.body").value("본문"))
            .andExpect(jsonPath("$.message.data.notificationId").value("1"))
            .andRespond(withSuccess("""{"name":"m-1"}""", MediaType.APPLICATION_JSON))

        provider.send(command())

        server.verify()
    }

    @Test
    fun `UNREGISTERED는 무효 Token으로 분류하고 재시도하지 않는다`() {
        respondWithFcmError(HttpStatus.NOT_FOUND, status = "NOT_FOUND", errorCode = "UNREGISTERED")

        val result = provider.send(command()) as PushSendResult.Failure

        assertThat(result.invalidToken).isTrue()
        assertThat(result.retryable).isFalse()
    }

    @Test
    fun `SENDER_ID_MISMATCH는 무효 Token으로 분류한다`() {
        respondWithFcmError(HttpStatus.FORBIDDEN, status = "PERMISSION_DENIED", errorCode = "SENDER_ID_MISMATCH")

        val result = provider.send(command()) as PushSendResult.Failure

        assertThat(result.invalidToken).isTrue()
    }

    @Test
    fun `UNAVAILABLE은 재시도 가능하고 무효 Token은 아니다`() {
        respondWithFcmError(HttpStatus.SERVICE_UNAVAILABLE, status = "UNAVAILABLE", errorCode = null)

        val result = provider.send(command()) as PushSendResult.Failure

        assertThat(result.retryable).isTrue()
        assertThat(result.invalidToken).isFalse()
    }

    @Test
    fun `INVALID_ARGUMENT는 재시도하지 않지만 기기를 무효로 보지 않는다`() {
        // 우리 쪽 Payload 구성 오류일 수도 있어 기기를 지우면 안 된다(FcmPushProvider KDoc 참고).
        respondWithFcmError(HttpStatus.BAD_REQUEST, status = "INVALID_ARGUMENT", errorCode = "INVALID_ARGUMENT")

        val result = provider.send(command()) as PushSendResult.Failure

        assertThat(result.retryable).isFalse()
        assertThat(result.invalidToken).isFalse()
    }

    @Test
    fun `해석할 수 없는 오류 응답은 재시도 가능으로 분류한다`() {
        server
            .expect(requestTo("https://oauth2.googleapis.com/token"))
            .andRespond(withSuccess("""{"access_token":"token-1","expires_in":3600}""", MediaType.APPLICATION_JSON))
        server
            .expect(requestTo("https://fcm.googleapis.com/v1/projects/$projectId/messages:send"))
            .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("<html>gateway</html>"))

        val result = provider.send(command()) as PushSendResult.Failure

        assertThat(result.code).isEqualTo("MALFORMED_RESPONSE")
        assertThat(result.retryable).isTrue()
    }

    @Test
    fun `Access Token을 발급받지 못하면 재시도 가능 실패로 분류하고 FCM을 호출하지 않는다`() {
        server
            .expect(requestTo("https://oauth2.googleapis.com/token"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED))

        val result = provider.send(command()) as PushSendResult.Failure

        assertThat(result.retryable).isTrue()
        server.verify()
    }

    @Test
    fun `FCM이 설정되지 않으면 재시도 가능 실패를 반환하고 아무 것도 호출하지 않는다`() {
        val unconfiguredRestClient = RestClient.builder().build()
        val unconfiguredProperties = FcmProperties()
        val disabled =
            FcmPushProvider(
                unconfiguredRestClient,
                unconfiguredProperties,
                FcmAccessTokenProvider(unconfiguredRestClient, unconfiguredProperties, objectMapper),
            )

        val result = disabled.send(command()) as PushSendResult.Failure

        assertThat(result.retryable).isTrue()
        assertThat(result.code).isEqualTo("NOT_CONFIGURED")
    }

    private fun respondWithFcmError(
        httpStatus: HttpStatus,
        status: String,
        errorCode: String?,
    ) {
        server
            .expect(requestTo("https://oauth2.googleapis.com/token"))
            .andRespond(withSuccess("""{"access_token":"token-1","expires_in":3600}""", MediaType.APPLICATION_JSON))
        val details =
            if (errorCode != null) {
                ""","details":[{"@type":"type.googleapis.com/google.firebase.fcm.v1.FcmError","errorCode":"$errorCode"}]"""
            } else {
                ""
            }
        server
            .expect(requestTo("https://fcm.googleapis.com/v1/projects/$projectId/messages:send"))
            .andRespond(
                withStatus(httpStatus)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"error":{"code":${httpStatus.value()},"message":"failed","status":"$status"$details}}"""),
            )
    }

    private fun command() =
        PushSendCommand(
            platform = PushPlatform.ANDROID,
            token = "device-token-1",
            title = "제목",
            body = "본문",
            data = mapOf("notificationId" to "1"),
        )

    private fun serviceAccountJson(): String {
        val privateKeyPem = generateTestPrivateKeyPem()
        val escaped = privateKeyPem.replace("\n", "\\n")
        return """
            {
              "type": "service_account",
              "project_id": "$projectId",
              "private_key": "$escaped",
              "client_email": "push@$projectId.iam.gserviceaccount.com",
              "token_uri": "https://oauth2.googleapis.com/token"
            }
            """.trimIndent()
    }

    private fun generateTestPrivateKeyPem(): String {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.genKeyPair()
        val base64 = Base64.getMimeEncoder(64, byteArrayOf('\n'.code.toByte())).encodeToString(keyPair.private.encoded)
        return "-----BEGIN PRIVATE KEY-----\n$base64\n-----END PRIVATE KEY-----\n"
    }
}
