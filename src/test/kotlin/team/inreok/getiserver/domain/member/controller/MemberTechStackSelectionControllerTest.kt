package team.inreok.getiserver.domain.member.controller

import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
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

@WebMvcTest(controllers = [MemberTechStackSelectionController::class])
class MemberTechStackSelectionControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var memberTechStackSelectionService: MemberTechStackSelectionService

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
                        .param("memberId", "1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"techStackIds":[10]}"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.techStacks.length()").value(1))
                .andExpect(jsonPath("$.data.techStacks[0].techStackId").value(10))
        }

        @Test
        fun `존재하지 않는 기술 스택이면 404 TECH_STACK_NOT_FOUND를 반환한다`() {
            given(memberTechStackSelectionService.replaceAll(1L, listOf(999L)))
                .willThrow(TechStackNotFoundException(listOf(999L)))

            mockMvc
                .perform(
                    patch("/api/v1/me/tech-stacks")
                        .param("memberId", "1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"techStackIds":[999]}"""),
                ).andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("TECH_STACK_NOT_FOUND"))
        }

        @Test
        fun `Authorization Header가 없으면 400을 반환한다`() {
            mockMvc
                .perform(
                    patch("/api/v1/me/tech-stacks")
                        .param("memberId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"techStackIds":[]}"""),
                ).andExpect(status().isBadRequest)
        }
    }
