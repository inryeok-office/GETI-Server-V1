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
import team.inreok.getiserver.domain.auth.exception.OAuthLoginFailedException
import team.inreok.getiserver.domain.auth.exception.OAuthStateInvalidException
import team.inreok.getiserver.domain.auth.exception.UnsupportedOAuthProviderException
import team.inreok.getiserver.domain.auth.service.OAuthLoginService
import team.inreok.getiserver.domain.auth.service.OAuthUserInfo

@WebMvcTest(controllers = [OAuthController::class])
class OAuthControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var oAuthLoginService: OAuthLoginService

        @Test
        fun `콜백 요청이 성공하면 200과 함께 Provider 사용자 식별값을 반환한다`() {
            given(oAuthLoginService.exchangeCode("google", "auth-code", "state-value"))
                .willReturn(OAuthUserInfo(subject = "google-subject-1", email = "teacher@example.com"))

            mockMvc
                .perform(get("/api/v1/auth/google/callback").param("code", "auth-code").param("state", "state-value"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.subject").value("google-subject-1"))
                .andExpect(jsonPath("$.data.email").value("teacher@example.com"))
        }

        @Test
        fun `지원하지 않는 Provider이면 400을 반환한다`() {
            willThrow(UnsupportedOAuthProviderException("dg"))
                .given(oAuthLoginService)
                .exchangeCode("dg", "auth-code", "state-value")

            mockMvc
                .perform(get("/api/v1/auth/dg/callback").param("code", "auth-code").param("state", "state-value"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_OAUTH_PROVIDER"))
        }

        @Test
        fun `state가 유효하지 않으면 400을 반환한다`() {
            willThrow(OAuthStateInvalidException())
                .given(oAuthLoginService)
                .exchangeCode("google", "auth-code", "invalid-state")

            mockMvc
                .perform(get("/api/v1/auth/google/callback").param("code", "auth-code").param("state", "invalid-state"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("OAUTH_STATE_INVALID"))
        }

        @Test
        fun `Google Token 교환이 실패하면 401을 반환한다`() {
            willThrow(OAuthLoginFailedException("Google 로그인에 실패했습니다."))
                .given(oAuthLoginService)
                .exchangeCode("google", "invalid-code", "state-value")

            mockMvc
                .perform(
                    get("/api/v1/auth/google/callback").param("code", "invalid-code").param("state", "state-value"),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("OAUTH_LOGIN_FAILED"))
                .andExpect(jsonPath("$.error.message").value("Google 로그인에 실패했습니다."))
        }
    }
