package team.inreok.getiserver.domain.auth.controller

import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.auth.dto.OAuthLoginResponse
import team.inreok.getiserver.domain.auth.exception.OAuthLoginFailedException
import team.inreok.getiserver.domain.auth.exception.OAuthStateInvalidException
import team.inreok.getiserver.domain.auth.exception.UnsupportedOAuthProviderException
import team.inreok.getiserver.domain.auth.service.AuthLoginService
import team.inreok.getiserver.domain.auth.service.OAuthLoginService
import team.inreok.getiserver.domain.member.exception.OAuthEmailAlreadyRegisteredException

@WebMvcTest(controllers = [OAuthController::class])
class OAuthControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        // OAuthController는 /authorize용 OAuthLoginService와 /callback용 AuthLoginService를 모두
        // 주입받으므로 두 Bean 모두 Mock으로 제공한다.
        @MockitoBean
        private lateinit var oAuthLoginService: OAuthLoginService

        @MockitoBean
        private lateinit var authLoginService: AuthLoginService

        @Test
        fun `콜백이 성공하면 200과 함께 Token 및 회원 정보를 반환한다`() {
            given(authLoginService.loginWithOAuth("google", "auth-code", "state-value"))
                .willReturn(
                    OAuthLoginResponse(
                        accessToken = "access-token",
                        refreshToken = "refresh-token",
                        accessTokenExpiresInSeconds = 1800,
                        memberId = 1L,
                        roles = listOf("TEACHER"),
                        status = "PENDING",
                        isNewMember = true,
                    ),
                )

            mockMvc
                .perform(get("/api/v1/auth/google/callback").param("code", "auth-code").param("state", "state-value"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.data.accessTokenExpiresInSeconds").value(1800))
                .andExpect(jsonPath("$.data.memberId").value(1))
                .andExpect(jsonPath("$.data.roles[0]").value("TEACHER"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.isNewMember").value(true))
        }

        @Test
        fun `지원하지 않는 Provider이면 400을 반환한다`() {
            willThrow(UnsupportedOAuthProviderException("kakao"))
                .given(authLoginService)
                .loginWithOAuth("kakao", "auth-code", "state-value")

            mockMvc
                .perform(get("/api/v1/auth/kakao/callback").param("code", "auth-code").param("state", "state-value"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_OAUTH_PROVIDER"))
        }

        @Test
        fun `state가 유효하지 않으면 400을 반환한다`() {
            willThrow(OAuthStateInvalidException())
                .given(authLoginService)
                .loginWithOAuth("google", "auth-code", "invalid-state")

            mockMvc
                .perform(get("/api/v1/auth/google/callback").param("code", "auth-code").param("state", "invalid-state"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("OAUTH_STATE_INVALID"))
        }

        @Test
        fun `Token 교환이 실패하면 401을 반환한다`() {
            willThrow(OAuthLoginFailedException("Google 로그인에 실패했습니다."))
                .given(authLoginService)
                .loginWithOAuth("google", "invalid-code", "state-value")

            mockMvc
                .perform(
                    get("/api/v1/auth/google/callback").param("code", "invalid-code").param("state", "state-value"),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("OAUTH_LOGIN_FAILED"))
                .andExpect(jsonPath("$.error.message").value("Google 로그인에 실패했습니다."))
        }

        @Test
        fun `이미 다른 방식으로 가입된 이메일이면 409를 반환한다`() {
            willThrow(OAuthEmailAlreadyRegisteredException("teacher@example.com"))
                .given(authLoginService)
                .loginWithOAuth("google", "auth-code", "state-value")

            mockMvc
                .perform(get("/api/v1/auth/google/callback").param("code", "auth-code").param("state", "state-value"))
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.error.code").value("OAUTH_EMAIL_ALREADY_REGISTERED"))
        }
    }
