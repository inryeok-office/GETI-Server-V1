package team.inreok.getiserver.domain.member.controller

import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.member.dto.MemberTechStacksResponse
import team.inreok.getiserver.domain.member.dto.TechStackResponse
import team.inreok.getiserver.domain.member.entity.type.TechStackCategory
import team.inreok.getiserver.domain.member.exception.TechStackNotFoundException
import team.inreok.getiserver.domain.member.service.MemberTechStackSelectionService
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig

// SecurityConfig를 명시적으로 Import해 /api/v1/me/**가 실제로 인증을 요구하는지(401)까지 검증한다.
@WebMvcTest(controllers = [MemberTechStackSelectionController::class])
@Import(SecurityConfig::class)
@EnableWebSecurity
class MemberTechStackSelectionControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var memberTechStackSelectionService: MemberTechStackSelectionService

        @MockitoBean
        private lateinit var jwtTokenProvider: JwtTokenProvider

        private fun authOf(
            memberId: Long,
            role: String = "STUDENT",
        ) = authentication(
            UsernamePasswordAuthenticationToken(memberId, null, listOf(SimpleGrantedAuthority("ROLE_$role"))),
        )

        @Test
        fun `내 기술 스택을 교체하면 200과 함께 변경된 목록을 반환한다`() {
            given(memberTechStackSelectionService.replaceAll(1L, listOf(10L))).willReturn(
                MemberTechStacksResponse(
                    listOf(TechStackResponse(techStackId = 10L, name = "Kotlin", category = TechStackCategory.BACKEND)),
                ),
            )

            mockMvc
                .perform(
                    patch("/api/v1/me/tech-stacks")
                        .with(authOf(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"techStackIds":[10]}"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.techStacks.length()").value(1))
                .andExpect(jsonPath("$.data.techStacks[0].techStackId").value(10))
        }

        @Test
        fun `Query Parameter로 다른 memberId를 보내도 인증된 본인 memberId로만 처리한다`() {
            given(memberTechStackSelectionService.replaceAll(1L, listOf(10L))).willReturn(
                MemberTechStacksResponse(
                    listOf(TechStackResponse(techStackId = 10L, name = "Kotlin", category = TechStackCategory.BACKEND)),
                ),
            )

            mockMvc
                .perform(
                    patch("/api/v1/me/tech-stacks")
                        .with(authOf(1L))
                        .param("memberId", "999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"techStackIds":[10]}"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.techStacks[0].techStackId").value(10))
        }

        @Test
        fun `존재하지 않는 기술 스택이면 404 TECH_STACK_NOT_FOUND를 반환한다`() {
            given(memberTechStackSelectionService.replaceAll(1L, listOf(999L)))
                .willThrow(TechStackNotFoundException(listOf(999L)))

            mockMvc
                .perform(
                    patch("/api/v1/me/tech-stacks")
                        .with(authOf(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"techStackIds":[999]}"""),
                ).andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("TECH_STACK_NOT_FOUND"))
        }

        @Test
        fun `요청자가 STUDENT가 아니면 403 NOT_A_STUDENT를 반환한다`() {
            mockMvc
                .perform(
                    patch("/api/v1/me/tech-stacks")
                        .with(authOf(1L, role = "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"techStackIds":[10]}"""),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("NOT_A_STUDENT"))
        }

        @Test
        fun `인증되지 않은 요청은 401 UNAUTHORIZED를 반환한다`() {
            mockMvc
                .perform(
                    patch("/api/v1/me/tech-stacks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"techStackIds":[]}"""),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        }
    }
