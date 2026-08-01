package team.inreok.getiserver.domain.auth.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import team.inreok.getiserver.domain.auth.exception.UnsupportedOAuthProviderException
import team.inreok.getiserver.domain.auth.service.impl.OAuthLoginServiceImpl
import team.inreok.getiserver.domain.auth.service.impl.OAuthProviderClient

class OAuthLoginServiceTest {
    // 실제 HTTP 없이 Router 동작만 검증하기 위한 가짜 Provider Client.
    private fun stub(
        providerName: String,
        authorization: OAuthAuthorization = OAuthAuthorization("$providerName-url", "state"),
        userInfo: OAuthUserInfo = OAuthUserInfo("$providerName-subject", "$providerName@example.com"),
    ) = object : OAuthProviderClient {
        override val provider: String = providerName

        override fun createAuthorization(): OAuthAuthorization = authorization

        override fun exchangeCode(
            code: String,
            state: String,
        ): OAuthUserInfo = userInfo
    }

    @Test
    fun `provider에 맞는 Client로 위임한다`() {
        val service = OAuthLoginServiceImpl(listOf(stub("google"), stub("dg")))

        assertThat(service.getAuthorizationUrl("google").authorizationUrl).isEqualTo("google-url")
        assertThat(service.exchangeCode("dg", "code", "state").subject).isEqualTo("dg-subject")
    }

    @Test
    fun `provider는 대소문자를 구분하지 않는다`() {
        val service = OAuthLoginServiceImpl(listOf(stub("dg")))

        assertThat(service.getAuthorizationUrl("DG").authorizationUrl).isEqualTo("dg-url")
    }

    @Test
    fun `등록되지 않은 provider면 UnsupportedOAuthProviderException을 던진다`() {
        val service = OAuthLoginServiceImpl(listOf(stub("google")))

        assertThatThrownBy { service.getAuthorizationUrl("kakao") }
            .isInstanceOf(UnsupportedOAuthProviderException::class.java)
    }
}
