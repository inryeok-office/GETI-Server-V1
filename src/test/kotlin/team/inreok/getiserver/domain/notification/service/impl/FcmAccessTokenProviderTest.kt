package team.inreok.getiserver.domain.notification.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import team.inreok.getiserver.domain.notification.config.FcmProperties
import tools.jackson.databind.ObjectMapper
import java.security.KeyPairGenerator
import java.util.Base64

class FcmAccessTokenProviderTest {
    private val objectMapper = ObjectMapper()
    private lateinit var server: MockRestServiceServer
    private lateinit var restClient: RestClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        restClient = builder.build()
    }

    @Test
    fun `설정이 없으면 null을 반환하고 Google을 호출하지 않는다`() {
        val provider = FcmAccessTokenProvider(restClient, FcmProperties(), objectMapper)

        assertThat(provider.currentAccessToken()).isNull()
        server.verify()
    }

    @Test
    fun `Service Account JSON에 필수 필드가 없으면 null을 반환한다`() {
        val incomplete = """{"project_id":"geti-app"}"""
        val provider =
            FcmAccessTokenProvider(
                restClient,
                FcmProperties(enabled = true, serviceAccountKey = incomplete),
                objectMapper,
            )

        assertThat(provider.currentAccessToken()).isNull()
    }

    @Test
    fun `정상 설정이면 JWT-bearer로 Access Token을 발급받는다`() {
        server
            .expect(requestTo("https://oauth2.googleapis.com/token"))
            .andExpect(method(org.springframework.http.HttpMethod.POST))
            .andRespond(
                withSuccess(
                    """{"access_token":"token-1","expires_in":3600,"token_type":"Bearer"}""",
                    MediaType.APPLICATION_JSON,
                ),
            )
        val provider =
            FcmAccessTokenProvider(
                restClient,
                FcmProperties(enabled = true, serviceAccountKey = serviceAccountJson()),
                objectMapper,
            )

        assertThat(provider.currentAccessToken()).isEqualTo("token-1")
        server.verify()
    }

    @Test
    fun `발급받은 Token은 만료 전까지 다시 요청하지 않는다`() {
        server
            .expect(requestTo("https://oauth2.googleapis.com/token"))
            .andRespond(withSuccess("""{"access_token":"token-1","expires_in":3600}""", MediaType.APPLICATION_JSON))
        val provider =
            FcmAccessTokenProvider(
                restClient,
                FcmProperties(enabled = true, serviceAccountKey = serviceAccountJson()),
                objectMapper,
            )

        provider.currentAccessToken()
        provider.currentAccessToken()

        // 두 번째 호출까지 Mock Expectation이 1건만 등록돼 있으므로, 다시 요청했다면 여기서 실패한다.
        server.verify()
    }

    @Test
    fun `Google이 access_token 없이 응답하면 null을 반환한다`() {
        server
            .expect(requestTo("https://oauth2.googleapis.com/token"))
            .andRespond(withSuccess("""{"token_type":"Bearer"}""", MediaType.APPLICATION_JSON))
        val provider =
            FcmAccessTokenProvider(
                restClient,
                FcmProperties(enabled = true, serviceAccountKey = serviceAccountJson()),
                objectMapper,
            )

        assertThat(provider.currentAccessToken()).isNull()
    }

    @Test
    fun `Google 호출이 실패하면 null을 반환한다`() {
        server
            .expect(requestTo("https://oauth2.googleapis.com/token"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED))
        val provider =
            FcmAccessTokenProvider(
                restClient,
                FcmProperties(enabled = true, serviceAccountKey = serviceAccountJson()),
                objectMapper,
            )

        assertThat(provider.currentAccessToken()).isNull()
    }

    @Test
    fun `projectId는 Service Account JSON의 project_id다`() {
        val provider =
            FcmAccessTokenProvider(
                restClient,
                FcmProperties(enabled = true, serviceAccountKey = serviceAccountJson()),
                objectMapper,
            )

        assertThat(provider.projectId()).isEqualTo("geti-app")
    }

    private fun serviceAccountJson(): String {
        val privateKeyPem = generateTestPrivateKeyPem()
        val escaped = privateKeyPem.replace("\n", "\\n")
        return """
            {
              "type": "service_account",
              "project_id": "geti-app",
              "private_key": "$escaped",
              "client_email": "push@geti-app.iam.gserviceaccount.com",
              "token_uri": "https://oauth2.googleapis.com/token"
            }
            """.trimIndent()
    }

    // 실제 서명 검증은 Google 쪽에서 하고 이 Test는 URL/Body Contract만 검증하므로, Test 전용으로
    // 매번 새로 만든 RSA Key로도 충분하다(실제 서비스 Key를 절대 Test에 넣지 않는다).
    private fun generateTestPrivateKeyPem(): String {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.genKeyPair()
        val base64 = Base64.getMimeEncoder(64, byteArrayOf('\n'.code.toByte())).encodeToString(keyPair.private.encoded)
        return "-----BEGIN PRIVATE KEY-----\n$base64\n-----END PRIVATE KEY-----\n"
    }
}
