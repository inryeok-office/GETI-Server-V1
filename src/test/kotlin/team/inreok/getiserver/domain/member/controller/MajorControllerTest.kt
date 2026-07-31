package team.inreok.getiserver.domain.member.controller

import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpHeaders
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.member.dto.MajorListResponse
import team.inreok.getiserver.domain.member.dto.MajorResponse
import team.inreok.getiserver.domain.member.service.MajorService

@WebMvcTest(controllers = [MajorController::class])
class MajorControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var majorService: MajorService

        @Test
        fun `전공 목록을 조회하면 200과 함께 항목 목록을 반환한다`() {
            given(majorService.search(null)).willReturn(
                MajorListResponse(listOf(MajorResponse(majorId = 1L, name = "소프트웨어", active = true))),
            )

            mockMvc
                .perform(get("/api/v1/metadata/majors").header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].majorId").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("소프트웨어"))
                .andExpect(jsonPath("$.data.items[0].active").value(true))
        }

        @Test
        fun `activeOnly Parameter를 그대로 Service에 전달한다`() {
            given(majorService.search(true)).willReturn(MajorListResponse(emptyList()))

            mockMvc
                .perform(
                    get("/api/v1/metadata/majors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                        .param("activeOnly", "true"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.items.length()").value(0))
        }

        @Test
        fun `Authorization Header가 없으면 400을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/metadata/majors"))
                .andExpect(status().isBadRequest)
        }
    }
