package team.inreok.getiserver.domain.auth.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import team.inreok.getiserver.domain.auth.exception.OAuthWebRedirectNotConfiguredException

class OAuthWebRedirectUriResolverTest {
    @Test
    fun `설정된 URL이 있으면 successUri는 그대로 반환한다`() {
        val resolver = OAuthWebRedirectUriResolver("https://web.example.com/auth/callback")

        assertThat(resolver.successUri().toString()).isEqualTo("https://web.example.com/auth/callback")
    }

    @Test
    fun `failureUri는 error Query Parameter만 덧붙인다`() {
        val resolver = OAuthWebRedirectUriResolver("https://web.example.com/auth/callback")

        assertThat(
            resolver.failureUri("OAUTH_LOGIN_FAILED").toString(),
        ).isEqualTo("https://web.example.com/auth/callback?error=OAUTH_LOGIN_FAILED")
    }

    @Test
    fun `설정이 비어 있으면 requireConfigured가 예외를 던진다`() {
        val resolver = OAuthWebRedirectUriResolver("")

        assertThatThrownBy { resolver.requireConfigured() }
            .isInstanceOf(OAuthWebRedirectNotConfiguredException::class.java)
    }

    @Test
    fun `설정이 비어 있으면 successUri도 예외를 던진다`() {
        val resolver = OAuthWebRedirectUriResolver("")

        assertThatThrownBy { resolver.successUri() }
            .isInstanceOf(OAuthWebRedirectNotConfiguredException::class.java)
    }

    @Test
    fun `설정이 비어 있으면 failureUri도 예외를 던진다`() {
        val resolver = OAuthWebRedirectUriResolver("")

        assertThatThrownBy { resolver.failureUri("OAUTH_LOGIN_FAILED") }
            .isInstanceOf(OAuthWebRedirectNotConfiguredException::class.java)
    }
}
