package team.inreok.getiserver.domain.auth.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import team.inreok.getiserver.domain.auth.exception.OAuthLoginFailedException
import team.inreok.getiserver.domain.auth.exception.OAuthStateInvalidException
import team.inreok.getiserver.domain.auth.exception.UnsupportedOAuthProviderException

/**
 * `DgOAuthProviderClientTest`에 대응하는 Google Provider Client 단위 Test다. createAuthorization은
 * HTTP 없이 URL/PKCE만 만들고, exchangeCode의 HTTP(토큰 교환·UserInfo)는 [MockRestServiceServer]로
 * 대체해 실제 Google을 호출하지 않는다.
 */
@ExtendWith(MockitoExtension::class)
class GoogleOAuthProviderClientTest {
    @Mock
    private lateinit var oAuthStateStore: OAuthStateStore

    private lateinit var server: MockRestServiceServer

    private fun client(
        clientId: String = "google-client",
        clientSecret: String = "google-secret",
        redirectUri: String = "http://localhost:8080/api/v1/auth/google/callback",
    ): GoogleOAuthProviderClient {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        return GoogleOAuthProviderClient(
            clientId = clientId,
            clientSecret = clientSecret,
            redirectUri = redirectUri,
            scope = "openid,email,profile",
            oAuthStateStore = oAuthStateStore,
            restClientBuilder = builder,
        )
    }

    @Test
    fun `provider 식별자는 google이다`() {
        assertThat(client().provider).isEqualTo("google")
    }

    @Test
    fun `createAuthorization은 PKCE(code_challenge S256)를 포함한 Google 인가 URL을 만들고 state를 저장한다`() {
        val result = client().createAuthorization()

        assertThat(result.authorizationUrl)
            .startsWith("https://accounts.google.com/o/oauth2/v2/auth")
            .contains("client_id=google-client")
            .contains("response_type=code")
            .contains("code_challenge=")
            .contains("code_challenge_method=S256")
            .contains("state=${result.state}")
        // state -> code_verifier 매핑이 Redis(OAuthStateStore)에 저장된다.
        verify(oAuthStateStore).save(anyString(), anyString())
    }

    @Test
    fun `Client 정보가 비어 있으면 UnsupportedOAuthProviderException을 던진다`() {
        assertThatThrownBy { client(clientId = "").createAuthorization() }
            .isInstanceOf(UnsupportedOAuthProviderException::class.java)
    }

    @Test
    fun `state가 저장돼 있지 않으면 OAuthStateInvalidException을 던진다`() {
        val client = client()
        given(oAuthStateStore.consume("unknown-state")).willReturn(null)

        assertThatThrownBy { client.exchangeCode("auth-code", "unknown-state") }
            .isInstanceOf(OAuthStateInvalidException::class.java)
    }

    @Test
    fun `email_verified가 true가 아니면 OAuthLoginFailedException을 던진다`() {
        val client = client()
        given(oAuthStateStore.consume("state-value")).willReturn("code-verifier")
        server
            .expect(requestTo("https://oauth2.googleapis.com/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""{"access_token":"google-access-token"}""", MediaType.APPLICATION_JSON))
        server
            .expect(requestTo("https://openidconnect.googleapis.com/v1/userinfo"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"sub":"google-subject-1","email":"teacher@example.com","email_verified":false}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertThatThrownBy { client.exchangeCode("auth-code", "state-value") }
            .isInstanceOf(OAuthLoginFailedException::class.java)
        server.verify()
    }
}
