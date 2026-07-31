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
import team.inreok.getiserver.domain.member.dto.MemberMajorItemResponse
import team.inreok.getiserver.domain.member.dto.MemberMajorsResponse
import team.inreok.getiserver.domain.member.exception.DuplicateMajorException
import team.inreok.getiserver.domain.member.exception.MajorNotFoundException
import team.inreok.getiserver.domain.member.service.MemberMajorService

@WebMvcTest(controllers = [MemberMajorController::class])
class MemberMajorControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var memberMajorService: MemberMajorService

        @Test
        fun `내 전공을 교체하면 200과 함께 변경된 목록을 반환한다`() {
            given(memberMajorService.replaceAll(1L, listOf(10L, 20L))).willReturn(
                MemberMajorsResponse(
                    listOf(
                        MemberMajorItemResponse(10L, "소프트웨어"),
                        MemberMajorItemResponse(20L, "인공지능"),
                    ),
                ),
            )

            mockMvc
                .perform(
                    patch("/api/v1/me/majors")
                        .param("memberId", "1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"majorIds":[10,20]}"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.majors.length()").value(2))
                .andExpect(jsonPath("$.data.majors[0].majorId").value(10))
        }

        @Test
        fun `존재하지 않는 전공이면 404 MAJOR_NOT_FOUND를 반환한다`() {
            given(memberMajorService.replaceAll(1L, listOf(999L))).willThrow(MajorNotFoundException(listOf(999L)))

            mockMvc
                .perform(
                    patch("/api/v1/me/majors")
                        .param("memberId", "1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"majorIds":[999]}"""),
                ).andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("MAJOR_NOT_FOUND"))
        }

        @Test
        fun `중복된 majorId가 있으면 409 DUPLICATE_MAJOR를 반환한다`() {
            given(memberMajorService.replaceAll(1L, listOf(10L, 10L))).willThrow(DuplicateMajorException())

            mockMvc
                .perform(
                    patch("/api/v1/me/majors")
                        .param("memberId", "1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"majorIds":[10,10]}"""),
                ).andExpect(status().isConflict)
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_MAJOR"))
        }

        @Test
        fun `Authorization Header가 없으면 400을 반환한다`() {
            mockMvc
                .perform(
                    patch("/api/v1/me/majors")
                        .param("memberId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"majorIds":[]}"""),
                ).andExpect(status().isBadRequest)
        }
    }
