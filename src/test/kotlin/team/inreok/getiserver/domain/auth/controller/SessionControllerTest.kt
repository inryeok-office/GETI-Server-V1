package team.inreok.getiserver.domain.auth.controller

import jakarta.servlet.http.Cookie
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.willThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.auth.exception.InvalidRefreshTokenException
import team.inreok.getiserver.domain.auth.service.RefreshTokenCookieFactory
import team.inreok.getiserver.domain.auth.service.TokenService
import team.inreok.getiserver.global.security.JwtProperties
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig

@WebMvcTest(controllers = [SessionController::class])
@Import(SecurityConfig::class, RefreshTokenCookieFactory::class)
@EnableWebSecurity
class SessionControllerTest
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

        private fun authOf(
            memberId: Long,
            vararg roles: String,
        ) = authentication(
            UsernamePasswordAuthenticationToken(
                memberId,
                null,
                roles.map { SimpleGrantedAuthority("ROLE_$it") },
            ),
        )

        @Test
        fun `세션 조회는 인증된 사용자의 memberId와 roles를 반환한다`() {
            mockMvc
                .perform(get("/api/v1/auth/session").with(authOf(7L, "STUDENT")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.memberId").value(7))
                .andExpect(jsonPath("$.data.roles[0]").value("STUDENT"))
        }

        @Test
        fun `인증 없이 세션을 조회하면 401을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/auth/session"))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `Body의 Refresh Token으로 로그아웃하면 204와 만료 Set-Cookie를 반환한다`() {
            mockMvc
                .perform(
                    delete("/api/v1/auth/logout")
                        .with(authOf(1L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"refreshToken":"raw-refresh-token"}"""),
                ).andExpect(status().isNoContent)
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
        }

        @Test
        fun `Cookie의 Refresh Token으로도 로그아웃한다`() {
            mockMvc
                .perform(
                    delete("/api/v1/auth/logout")
                        .with(authOf(1L, "STUDENT"))
                        .cookie(Cookie("refreshToken", "cookie-token")),
                ).andExpect(status().isNoContent)
        }

        @Test
        fun `다른 사용자 소유의 Refresh Token으로 로그아웃하면 401을 반환한다`() {
            willThrow(InvalidRefreshTokenException())
                .given(tokenService)
                .logout("raw-refresh-token", 1L)

            mockMvc
                .perform(
                    delete("/api/v1/auth/logout")
                        .with(authOf(1L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"refreshToken":"raw-refresh-token"}"""),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"))
        }

        @Test
        fun `Cookie-Header-Body 어디에도 Refresh Token이 없으면 400을 반환한다`() {
            mockMvc
                .perform(delete("/api/v1/auth/logout").with(authOf(1L, "STUDENT")))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_REQUIRED"))
        }

        @Test
        fun `인증 없이 로그아웃하면 401을 반환한다`() {
            mockMvc
                .perform(
                    delete("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"refreshToken":"raw-refresh-token"}"""),
                ).andExpect(status().isUnauthorized)
        }
    }
