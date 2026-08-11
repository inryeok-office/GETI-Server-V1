package team.inreok.getiserver.domain.auth.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.auth.service.IssuedTokens
import team.inreok.getiserver.domain.auth.service.OAuthLoginService
import team.inreok.getiserver.domain.auth.service.OAuthUserInfo
import team.inreok.getiserver.domain.auth.service.TokenService
import team.inreok.getiserver.domain.member.query.OAuthMemberIdentity
import team.inreok.getiserver.domain.member.query.OAuthMemberPort

@ExtendWith(MockitoExtension::class)
class AuthLoginServiceImplTest {
    @Mock
    private lateinit var oAuthLoginService: OAuthLoginService

    @Mock
    private lateinit var oAuthMemberPort: OAuthMemberPort

    @Mock
    private lateinit var tokenService: TokenService

    private val service: AuthLoginServiceImpl by lazy {
        AuthLoginServiceImpl(oAuthLoginService, oAuthMemberPort, tokenService)
    }

    @Test
    fun `로그인은 code 교환-회원 조회_생성-Token 발급을 조합해 응답을 만든다`() {
        given(oAuthLoginService.exchangeCode("google", "auth-code", "state-value"))
            .willReturn(OAuthUserInfo(subject = "google-subject-1", email = "teacher@example.com"))
        given(oAuthMemberPort.findOrCreateByOAuth("google", "google-subject-1", "teacher@example.com"))
            .willReturn(
                OAuthMemberIdentity(
                    memberId = 7L,
                    status = "PENDING",
                    roles = listOf("TEACHER"),
                    isNewMember = true,
                ),
            )
        given(tokenService.issueFor(7L, listOf("TEACHER"), null))
            .willReturn(
                IssuedTokens(
                    accessToken = "access-token",
                    refreshToken = "refresh-token",
                    accessTokenExpiresInSeconds = 1800,
                ),
            )

        val response = service.loginWithOAuth("google", "auth-code", "state-value")

        assertThat(response.accessToken).isEqualTo("access-token")
        assertThat(response.refreshToken).isEqualTo("refresh-token")
        assertThat(response.accessTokenExpiresInSeconds).isEqualTo(1800)
        assertThat(response.memberId).isEqualTo(7L)
        assertThat(response.roles).containsExactly("TEACHER")
        assertThat(response.status).isEqualTo("PENDING")
        assertThat(response.isNewMember).isTrue()
    }

    @Test
    fun `발급 시 회원의 실제 roles를 Token 발급에 그대로 전달한다`() {
        given(oAuthLoginService.exchangeCode("google", "auth-code", "state-value"))
            .willReturn(OAuthUserInfo(subject = "subject-2", email = "dev@example.com"))
        given(oAuthMemberPort.findOrCreateByOAuth("google", "subject-2", "dev@example.com"))
            .willReturn(
                OAuthMemberIdentity(
                    memberId = 9L,
                    status = "ACTIVE",
                    roles = listOf("DEVELOPER"),
                    isNewMember = false,
                ),
            )
        given(tokenService.issueFor(9L, listOf("DEVELOPER"), null))
            .willReturn(IssuedTokens("a", "r", 1800))

        val response = service.loginWithOAuth("google", "auth-code", "state-value")

        assertThat(response.roles).containsExactly("DEVELOPER")
        assertThat(response.isNewMember).isFalse()
    }
}
