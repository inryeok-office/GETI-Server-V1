package team.inreok.getiserver.domain.auth.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.auth.entity.RefreshToken
import team.inreok.getiserver.domain.auth.exception.InvalidRefreshTokenException
import team.inreok.getiserver.domain.auth.repository.RefreshTokenRepository
import team.inreok.getiserver.global.security.JwtProperties
import team.inreok.getiserver.global.security.JwtTokenProvider
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class TokenServiceImplTest {
    @Mock
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Mock
    private lateinit var jwtTokenProvider: JwtTokenProvider

    private val jwtProperties =
        JwtProperties(
            secret = "test-secret",
            accessTokenExpirationSeconds = 1800,
            refreshTokenExpirationSeconds = 1209600,
        )

    private val service: TokenServiceImpl by lazy {
        TokenServiceImpl(refreshTokenRepository, jwtTokenProvider, jwtProperties)
    }

    private fun refreshToken(
        memberId: Long = 1L,
        expiresAt: LocalDateTime = LocalDateTime.now().plusDays(1),
    ) = RefreshToken(memberId = memberId, tokenHash = "stored-hash", expiresAt = expiresAt)

    // Kotlin non-null 파라미터(LocalDateTime)에 bare any()를 쓰면 null 반환으로 NPE가 나므로,
    // Matcher를 등록하되 non-null 값을 반환하는 Helper를 사용한다.
    private fun anyLocalDateTime(): LocalDateTime = any(LocalDateTime::class.java) ?: LocalDateTime.now()

    @Test
    fun `refresh는 원자적 폐기 후 새 Access-Refresh Token을 발급한다`() {
        given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(refreshToken())
        given(refreshTokenRepository.revokeIfActive(anyString(), anyLocalDateTime())).willReturn(1)
        given(jwtTokenProvider.createAccessToken(1L, emptyList())).willReturn("access-token")

        val result = service.refresh("raw-refresh-token", deviceIdentifier = null)

        assertThat(result.accessToken).isEqualTo("access-token")
        assertThat(result.refreshToken).isNotBlank()
        assertThat(result.accessTokenExpiresInSeconds).isEqualTo(1800)
        verify(refreshTokenRepository).save(any())
    }

    @Test
    fun `refresh는 저장된 Token이 없으면 예외를 던지고 폐기를 시도하지 않는다`() {
        given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(null)

        assertThatThrownBy { service.refresh("raw-refresh-token", null) }
            .isInstanceOf(InvalidRefreshTokenException::class.java)
        verify(refreshTokenRepository, never()).revokeIfActive(anyString(), anyLocalDateTime())
    }

    @Test
    fun `refresh는 만료된 Token이면 예외를 던지고 폐기를 시도하지 않는다`() {
        given(refreshTokenRepository.findByTokenHash(anyString()))
            .willReturn(refreshToken(expiresAt = LocalDateTime.now().minusMinutes(1)))

        assertThatThrownBy { service.refresh("raw-refresh-token", null) }
            .isInstanceOf(InvalidRefreshTokenException::class.java)
        verify(refreshTokenRepository, never()).revokeIfActive(anyString(), anyLocalDateTime())
    }

    @Test
    fun `refresh는 이미 폐기된 Token이면(영향 행 0) 예외를 던지고 새 Token을 발급하지 않는다`() {
        given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(refreshToken())
        given(refreshTokenRepository.revokeIfActive(anyString(), anyLocalDateTime())).willReturn(0)

        assertThatThrownBy { service.refresh("raw-refresh-token", null) }
            .isInstanceOf(InvalidRefreshTokenException::class.java)
        verify(refreshTokenRepository, never()).save(any())
    }

    @Test
    fun `logout은 호출자가 Token 소유자가 아니면 거부하고 폐기하지 않는다`() {
        given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(refreshToken(memberId = 1L))

        assertThatThrownBy { service.logout("raw-refresh-token", memberId = 999L) }
            .isInstanceOf(InvalidRefreshTokenException::class.java)
        verify(refreshTokenRepository, never()).revokeIfActive(anyString(), anyLocalDateTime())
    }

    @Test
    fun `logout은 소유자면 Token을 폐기한다`() {
        given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(refreshToken(memberId = 1L))
        given(refreshTokenRepository.revokeIfActive(anyString(), anyLocalDateTime())).willReturn(1)

        service.logout("raw-refresh-token", memberId = 1L)

        verify(refreshTokenRepository).revokeIfActive(anyString(), anyLocalDateTime())
    }
}
