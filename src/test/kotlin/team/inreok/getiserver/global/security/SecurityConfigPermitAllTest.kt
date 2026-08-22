package team.inreok.getiserver.global.security

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME

// 운영 SecurityConfig(securityFilterChain)가 정상 인증/인가 정책을 강제하는지 검증한다. 임시
// 전역 permitAll 개방은 제거되었으므로(Issue #162), 미인증 요청은 401, 역할 부족은 403이어야 한다.
@WebMvcTest(controllers = [SecurityAuthorizationProbeController::class])
@Import(SecurityConfig::class)
@EnableWebSecurity
class SecurityConfigPermitAllTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var jwtTokenProvider: JwtTokenProvider

        private fun authOf(vararg roles: String) =
            authentication(
                UsernamePasswordAuthenticationToken(
                    1L,
                    null,
                    roles.map { SimpleGrantedAuthority("ROLE_$it") },
                ),
            )

        @Test
        fun `인증 없이 일반 API에 접근하면 401을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/jobs"))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        }

        @Test
        fun `인증 없이 me API에 접근하면 401을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/me/profile"))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        }

        @Test
        fun `인증된 사용자는 일반 API에 접근할 수 있다`() {
            mockMvc
                .perform(get("/api/v1/jobs").with(authOf("STUDENT")))
                .andExpect(status().isOk)
        }

        @Test
        fun `역할이 부족하면 admin API에 접근하면 403을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/admin/members").with(authOf("STUDENT")))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
        }

        @Test
        fun `적절한 역할이면 admin API에 접근할 수 있다`() {
            mockMvc
                .perform(get("/api/v1/admin/members").with(authOf("TEACHER")))
                .andExpect(status().isOk)
        }

        @Test
        fun `잘못된 Authorization Header는 미인증으로 처리되어 401을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/jobs").header("Authorization", "Bearer malformed-token"))
                .andExpect(status().isUnauthorized)
        }
    }

@RestController
@Tag(name = "Security Test", description = "Normal authorization policy verification")
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
private class SecurityAuthorizationProbeController {
    @Operation(summary = "Security authorization probe", description = "Verifies normal HTTP authorization rules.")
    @ApiResponses(ApiResponse(responseCode = "200", description = "Probe succeeded"))
    @GetMapping("/api/v1/jobs", "/api/v1/me/profile", "/api/v1/admin/members")
    fun probe() = PROBE_RESPONSE

    private companion object {
        private const val PROBE_RESPONSE = "ok"
    }
}
