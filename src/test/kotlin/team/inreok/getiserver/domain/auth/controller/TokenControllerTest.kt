package team.inreok.getiserver.domain.auth.controller

import jakarta.servlet.http.Cookie
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.auth.exception.InvalidRefreshTokenException
import team.inreok.getiserver.domain.auth.service.IssuedTokens
import team.inreok.getiserver.domain.auth.service.RefreshTokenCookieFactory
import team.inreok.getiserver.domain.auth.service.TokenService
import team.inreok.getiserver.global.security.JwtProperties
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig

// 실제 RefreshTokenCookieFactory를 Import해 @CookieValue/@RequestHeader 바인딩과 Set-Cookie 출력까지
// 검증한다. /refresh는 permitAll이므로 인증은 필요 없다.
@WebMvcTest(controllers = [TokenController::class])
@Import(SecurityConfig::class, RefreshTokenCookieFactory::class)
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

        @MockitoBean
        private lateinit var jwtProperties: JwtProperties

        private fun issued() =
            IssuedTokens(accessToken = "new-access", refreshToken = "new-refresh", accessTokenExpiresInSeconds = 1800)

        @Test
        fun `Body의 Refresh Token으로 재발급하면 200과 새 Token, Set-Cookie를 반환한다`() {
            given(jwtProperties.refreshTokenExpirationSeconds).willReturn(1209600)
            given(tokenService.refresh("body-token", null)).willReturn(issued())

            mockMvc
                .perform(
                    post("/api/v1/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"refreshToken":"body-token"}"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.accessToken").value("new-access"))
                .andExpect(jsonPath("$.data.refreshToken").value("new-refresh"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=new-refresh")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
        }

        @Test
        fun `Cookie의 Refresh Token이 Body보다 우선한다`() {
            given(jwtProperties.refreshTokenExpirationSeconds).willReturn(1209600)
            given(tokenService.refresh("cookie-token", null)).willReturn(issued())

            mockMvc
                .perform(
                    post("/api/v1/auth/token/refresh")
                        .cookie(Cookie("refreshToken", "cookie-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"refreshToken":"body-token"}"""),
                ).andExpect(status().isOk)
        }

        @Test
        fun `X-Refresh-Token Header의 Refresh Token으로도 재발급한다`() {
            given(jwtProperties.refreshTokenExpirationSeconds).willReturn(1209600)
            given(tokenService.refresh("header-token", null)).willReturn(issued())

            mockMvc
                .perform(
                    post("/api/v1/auth/token/refresh").header("X-Refresh-Token", "header-token"),
                ).andExpect(status().isOk)
        }

        @Test
        fun `Cookie-Header-Body 어디에도 Refresh Token이 없으면 400을 반환한다`() {
            mockMvc
                .perform(post("/api/v1/auth/token/refresh"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_REQUIRED"))
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
