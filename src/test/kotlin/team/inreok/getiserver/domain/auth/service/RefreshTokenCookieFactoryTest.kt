package team.inreok.getiserver.domain.auth.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import team.inreok.getiserver.global.security.JwtProperties
import java.time.Duration

class RefreshTokenCookieFactoryTest {
    private val jwtProperties =
        JwtProperties(
            secret = "test-secret",
            accessTokenExpirationSeconds = 1800,
            refreshTokenExpirationSeconds = 1209600,
        )

    private fun factory(
        secure: Boolean = false,
        sameSite: String = "Lax",
    ) = RefreshTokenCookieFactory(jwtProperties, secure, sameSite)

    @Test
    fun `create는 HttpOnly-Path-SameSite와 Refresh Token 수명을 담은 Cookie를 만든다`() {
        val cookie = factory(secure = true).create("refresh-token-value")

        assertThat(cookie.name).isEqualTo("refreshToken")
        assertThat(cookie.value).isEqualTo("refresh-token-value")
        assertThat(cookie.isHttpOnly).isTrue()
        assertThat(cookie.isSecure).isTrue()
        assertThat(cookie.path).isEqualTo("/api/v1/auth")
        assertThat(cookie.sameSite).isEqualTo("Lax")
        assertThat(cookie.maxAge).isEqualTo(Duration.ofSeconds(1209600))
    }

    @Test
    fun `expired는 값이 비고 Max-Age가 0인 Cookie를 만든다`() {
        val cookie = factory().expired()

        assertThat(cookie.value).isEmpty()
        assertThat(cookie.maxAge).isEqualTo(Duration.ZERO)
        assertThat(cookie.isHttpOnly).isTrue()
    }

    @Test
    fun `resolve는 Cookie를 최우선으로 고른다`() {
        assertThat(factory().resolve("cookie-token", "header-token", "body-token")).isEqualTo("cookie-token")
    }

    @Test
    fun `resolve는 Cookie가 없으면 Header를 고른다`() {
        assertThat(factory().resolve(null, "header-token", "body-token")).isEqualTo("header-token")
    }

    @Test
    fun `resolve는 Cookie-Header가 없으면 Body를 고른다`() {
        assertThat(factory().resolve(null, null, "body-token")).isEqualTo("body-token")
    }

    @Test
    fun `resolve는 공백 값을 건너뛴다`() {
        assertThat(factory().resolve("  ", "header-token", null)).isEqualTo("header-token")
    }

    @Test
    fun `resolve는 셋 다 없으면 null을 반환한다`() {
        assertThat(factory().resolve(null, null, null)).isNull()
        assertThat(factory().resolve("", "  ", "")).isNull()
    }
}
