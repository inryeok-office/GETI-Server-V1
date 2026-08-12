package team.inreok.getiserver.domain.auth.controller

import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.auth.exception.InvalidRefreshTokenException
import team.inreok.getiserver.domain.auth.service.IssuedTokens
import team.inreok.getiserver.domain.auth.service.TokenService
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig

// /api/v1/auth/token/refresh는 permitAll이지만, POST/CSRF·경로 규칙을 실제와 동일하게 검증하기 위해
// SecurityConfig를 Import한다(FormControllerTest와 동일한 패턴). JwtTokenProvider는 Filter 구성에만
// 필요하므로 Mock으로 제공한다.
@WebMvcTest(controllers = [TokenController::class])
@Import(SecurityConfig::class)
@EnableWebSecurity
class TokenControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var tokenService: TokenService

        @MockitoBean
        private lateinit var jwtTokenProvider: JwtTokenProvider

        @Test
        fun `재발급에 성공하면 200과 함께 새 Token 쌍을 반환한다`() {
            given(tokenService.refresh("raw-refresh-token", null))
                .willReturn(
                    IssuedTokens(
                        accessToken = "new-access",
                        refreshToken = "new-refresh",
                        accessTokenExpiresInSeconds = 1800,
                    ),
                )

            mockMvc
                .perform(
                    post("/api/v1/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"refreshToken":"raw-refresh-token"}"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.accessToken").value("new-access"))
                .andExpect(jsonPath("$.data.refreshToken").value("new-refresh"))
                .andExpect(jsonPath("$.data.accessTokenExpiresInSeconds").value(1800))
        }

        @Test
        fun `refreshToken이 비어 있으면 400을 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"refreshToken":""}"""),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
        }

        @Test
        fun `유효하지 않거나 만료·폐기된 Refresh Token이면 401을 반환한다`() {
            willThrow(InvalidRefreshTokenException())
                .given(tokenService)
                .refresh("expired-token", null)

            mockMvc
                .perform(
                    post("/api/v1/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"refreshToken":"expired-token"}"""),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"))
        }
    }
