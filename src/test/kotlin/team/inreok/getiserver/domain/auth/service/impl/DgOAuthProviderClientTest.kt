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
import org.springframework.web.client.RestClient
import team.inreok.getiserver.domain.auth.exception.UnsupportedOAuthProviderException

@ExtendWith(MockitoExtension::class)
class DgOAuthProviderClientTest {
    @Mock
    private lateinit var oAuthStateStore: OAuthStateStore

    @Mock
    private lateinit var restClientBuilder: RestClient.Builder

    @Mock
    private lateinit var restClient: RestClient

    // createAuthorization은 HTTP를 호출하지 않으므로(state 저장 + URL 생성) RestClient는 생성만 되고
    // 사용되지 않는다. 생성자에서 build()가 호출되므로 그 반환값만 준비한다.
    private fun client(
        clientId: String = "dg-client",
        clientSecret: String = "dg-secret",
        redirectUri: String = "http://localhost:8080/api/v1/auth/dg/callback",
    ): DgOAuthProviderClient {
        given(restClientBuilder.build()).willReturn(restClient)
        return DgOAuthProviderClient(
            clientId = clientId,
            clientSecret = clientSecret,
            redirectUri = redirectUri,
            authorizationServer = "https://oauth.authorization.datagsm.kr",
            resourceServer = "https://oauth.resource.datagsm.kr",
            oAuthStateStore = oAuthStateStore,
            restClientBuilder = restClientBuilder,
        )
    }

    @Test
    fun `provider 식별자는 dg다`() {
        assertThat(client().provider).isEqualTo("dg")
    }

    @Test
    fun `createAuthorization은 DG 인가 URL을 만들고 state를 저장한다`() {
        val result = client().createAuthorization()

        assertThat(result.authorizationUrl)
            .startsWith("https://oauth.authorization.datagsm.kr/v1/oauth/authorize")
            .contains("client_id=dg-client")
            .contains("response_type=code")
            .contains("state=${result.state}")
        // DG는 scope를 생략하고 PKCE(code_challenge)를 쓰지 않는다.
        assertThat(result.authorizationUrl).doesNotContain("scope")
        assertThat(result.authorizationUrl).doesNotContain("code_challenge")
        // state가 Redis(OAuthStateStore)에 저장되는지 확인한다. Kotlin non-null 파라미터에는 null을
        // 반환하는 eq()/any() 대신 빈 문자열을 반환하는 anyString()을 사용한다.
        verify(oAuthStateStore).save(anyString(), anyString())
    }

    @Test
    fun `Client 정보가 비어 있으면 UnsupportedOAuthProviderException을 던진다`() {
        assertThatThrownBy { client(clientId = "").createAuthorization() }
            .isInstanceOf(UnsupportedOAuthProviderException::class.java)
    }
}
