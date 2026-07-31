package team.inreok.getiserver.domain.member.controller

import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpHeaders
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.member.dto.TechStackListResponse
import team.inreok.getiserver.domain.member.dto.TechStackResponse
import team.inreok.getiserver.domain.member.entity.type.TechStackCategory
import team.inreok.getiserver.domain.member.service.TechStackService

@WebMvcTest(controllers = [TechStackController::class])
class TechStackControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var techStackService: TechStackService

        @Test
        fun `기술 스택 목록을 조회하면 200과 함께 항목 목록을 반환한다`() {
            given(techStackService.search(null, null)).willReturn(
                TechStackListResponse(
                    items =
                        listOf(
                            TechStackResponse(techStackId = 1L, name = "Kotlin", category = TechStackCategory.BACKEND),
                        ),
                ),
            )

            mockMvc
                .perform(get("/api/v1/metadata/tech-stacks").header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].techStackId").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("Kotlin"))
                .andExpect(jsonPath("$.data.items[0].category").value("BACKEND"))
        }

        @Test
        fun `query와 category Parameter를 그대로 Service에 전달한다`() {
            given(techStackService.search("kot", TechStackCategory.BACKEND))
                .willReturn(TechStackListResponse(items = emptyList()))

            mockMvc
                .perform(
                    get("/api/v1/metadata/tech-stacks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                        .param("query", "kot")
                        .param("category", "BACKEND"),
                ).andExpect(status().isOk)

            verify(techStackService).search("kot", TechStackCategory.BACKEND)
        }

        @Test
        fun `일치하는 항목이 없으면 빈 목록을 반환한다`() {
            given(techStackService.search("존재하지-않는-기술", null))
                .willReturn(TechStackListResponse(items = emptyList()))

            mockMvc
                .perform(
                    get("/api/v1/metadata/tech-stacks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                        .param("query", "존재하지-않는-기술"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.items.length()").value(0))
        }

        @Test
        fun `category가 정의되지 않은 값이면 400을 반환한다`() {
            mockMvc
                .perform(
                    get("/api/v1/metadata/tech-stacks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                        .param("category", "INVALID"),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `Authorization Header가 없으면 400을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/metadata/tech-stacks"))
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `Authorization Header가 빈 값이면 400을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/metadata/tech-stacks").header(HttpHeaders.AUTHORIZATION, ""))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
        }
    }
