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
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME

@WebMvcTest(controllers = [SecurityPermitAllProbeController::class])
@Import(SecurityConfig::class)
@EnableWebSecurity
class SecurityConfigPermitAllTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var jwtTokenProvider: JwtTokenProvider

        @Test
        fun `인증 없이 일반 API에 접근할 수 있다`() {
            mockMvc.perform(get("/api/v1/jobs")).andExpect(status().isOk)
        }

        @Test
        fun `인증 없이 me API에 접근할 수 있다`() {
            mockMvc.perform(get("/api/v1/me/profile")).andExpect(status().isOk)
        }

        @Test
        fun `인증 없이 admin API에 접근할 수 있다`() {
            mockMvc.perform(get("/api/v1/admin/members")).andExpect(status().isOk)
        }

        @Test
        fun `잘못된 Authorization Header가 있어도 HTTP Authorization에서 차단하지 않는다`() {
            mockMvc
                .perform(get("/api/v1/jobs").header("Authorization", "Bearer malformed-token"))
                .andExpect(status().isOk)
        }
    }

@RestController
@Tag(name = "Security Test", description = "Temporary permitAll security verification")
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
private class SecurityPermitAllProbeController {
    @Operation(summary = "Security permitAll probe", description = "Verifies HTTP authorization bypass.")
    @ApiResponses(ApiResponse(responseCode = "200", description = "Probe succeeded"))
    @GetMapping("/api/v1/jobs", "/api/v1/me/profile", "/api/v1/admin/members")
    fun probe() = PROBE_RESPONSE

    private companion object {
        private const val PROBE_RESPONSE = "ok"
    }
}
