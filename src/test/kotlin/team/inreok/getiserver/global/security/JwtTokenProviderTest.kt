package team.inreok.getiserver.global.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JwtTokenProviderTest {
    private fun provider(expirationSeconds: Long = 1800): JwtTokenProvider =
        JwtTokenProvider(
            JwtProperties(
                secret = "test-secret-that-is-long-enough-for-hs256-signing-key",
                accessTokenExpirationSeconds = expirationSeconds,
                refreshTokenExpirationSeconds = 1209600,
            ),
        )

    @Test
    fun `발급한 Access Token을 다시 파싱하면 memberId와 roles를 복원한다`() {
        val jwtTokenProvider = provider()

        val token = jwtTokenProvider.createAccessToken(memberId = 42L, roles = listOf("TEACHER", "STUDENT"))
        val claims = jwtTokenProvider.parseOrNull(token)

        assertThat(claims).isNotNull
        assertThat(JwtTokenProvider.memberIdOf(claims!!)).isEqualTo(42L)
        assertThat(JwtTokenProvider.rolesOf(claims)).containsExactly("TEACHER", "STUDENT")
    }

    @Test
    fun `서명이 변조된 Token은 null을 반환한다`() {
        val jwtTokenProvider = provider()
        val token = jwtTokenProvider.createAccessToken(memberId = 1L, roles = emptyList())

        val tampered = token.dropLast(3) + "xxx"

        assertThat(jwtTokenProvider.parseOrNull(tampered)).isNull()
    }

    @Test
    fun `JWT 형식이 아닌 문자열은 null을 반환한다`() {
        assertThat(provider().parseOrNull("not-a-jwt")).isNull()
    }

    @Test
    fun `만료된 Token은 null을 반환한다`() {
        // 만료 시간을 과거로 설정해 발급 즉시 만료된 Token을 만든다.
        val jwtTokenProvider = provider(expirationSeconds = -60)
        val token = jwtTokenProvider.createAccessToken(memberId = 1L, roles = emptyList())

        assertThat(jwtTokenProvider.parseOrNull(token)).isNull()
    }
}
