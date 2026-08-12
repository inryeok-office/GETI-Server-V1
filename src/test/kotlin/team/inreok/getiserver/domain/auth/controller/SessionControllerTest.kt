package team.inreok.getiserver.domain.auth.controller

import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.willThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.auth.exception.InvalidRefreshTokenException
import team.inreok.getiserver.domain.auth.service.TokenService
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig

// /api/v1/auth/session·/logout이 실제로 인증을 강제하는지(401)까지 검증하려면 SecurityConfig가
// 필요하므로 Import한다(FormControllerTest와 동일한 패턴).
@WebMvcTest(controllers = [SessionController::class])
@Import(SecurityConfig::class)
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

        // principal은 memberId(Long), authorities는 ROLE_ 접두어를 붙인 Role이다(JwtAuthenticationFilter와 동일).
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
        fun `로그아웃에 성공하면 204를 반환한다`() {
            mockMvc
                .perform(
                    delete("/api/v1/auth/logout")
                        .with(authOf(1L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"refreshToken":"raw-refresh-token"}"""),
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
        fun `refreshToken이 비어 있으면 400을 반환한다`() {
            mockMvc
                .perform(
                    delete("/api/v1/auth/logout")
                        .with(authOf(1L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"refreshToken":""}"""),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
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
