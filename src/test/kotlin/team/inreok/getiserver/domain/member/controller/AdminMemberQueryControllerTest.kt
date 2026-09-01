package team.inreok.getiserver.domain.member.controller

import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
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
import team.inreok.getiserver.domain.member.dto.AdminMemberListResponse
import team.inreok.getiserver.domain.member.dto.AdminMemberSummaryResponse
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.service.AdminMemberQueryService
import team.inreok.getiserver.global.security.JwtTokenProvider

// SecurityConfig(NormalSecurityTestConfig)를 Import해 /api/v1/admin/members가 실제로 DEVELOPER
// 권한을 요구하는지(401/403)까지 검증한다.
@WebMvcTest(controllers = [AdminMemberQueryController::class])
@Import(team.inreok.getiserver.global.security.NormalSecurityTestConfig::class)
@EnableWebSecurity
class AdminMemberQueryControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var adminMemberQueryService: AdminMemberQueryService

        @MockitoBean
        private lateinit var jwtTokenProvider: JwtTokenProvider

        @Test
        fun `개발자가 TEACHER 목록을 조회하면 200과 memberId·name을 반환한다`() {
            given(adminMemberQueryService.listActiveMembersByRole(anyRole()))
                .willReturn(
                    AdminMemberListResponse(
                        listOf(
                            AdminMemberSummaryResponse(memberId = 1L, name = "강교사"),
                            AdminMemberSummaryResponse(memberId = 2L, name = "김교사"),
                        ),
                    ),
                )

            mockMvc
                .perform(get("/api/v1/admin/members").param("role", "TEACHER").with(authOf(7L, "DEVELOPER")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.members[0].memberId").value(1))
                .andExpect(jsonPath("$.data.members[0].name").value("강교사"))
                .andExpect(jsonPath("$.data.members[1].memberId").value(2))
        }

        @Test
        fun `교사도 목록을 조회할 수 있다`() {
            given(adminMemberQueryService.listActiveMembersByRole(anyRole()))
                .willReturn(AdminMemberListResponse(listOf(AdminMemberSummaryResponse(memberId = 1L, name = "강교사"))))

            mockMvc
                .perform(get("/api/v1/admin/members").param("role", "TEACHER").with(authOf(9L, "TEACHER")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.members[0].memberId").value(1))
        }

        @Test
        fun `role 파라미터가 없으면 400을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/admin/members").with(authOf(7L, "DEVELOPER")))
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `교사·개발자가 아니면 403을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/admin/members").param("role", "TEACHER").with(authOf(7L, "STUDENT")))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
        }

        @Test
        fun `인증이 없으면 401을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/admin/members").param("role", "TEACHER"))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        }

        private fun anyRole(): RoleType = any(RoleType::class.java) ?: RoleType.TEACHER

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
    }
