package team.inreok.getiserver.web

import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.matchesPattern
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [WebTestSupportController::class])
class GlobalExceptionHandlerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @Test
        fun `성공 응답은 data Field에 결과를 담는다`() {
            mockMvc
                .perform(get("/test/web/success"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.id").value("1"))
        }

        @Test
        fun `Pagination 응답은 data와 meta로 구성된다`() {
            mockMvc
                .perform(get("/test/web/page"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(2))
                .andExpect(jsonPath("$.meta.totalElements").value(5))
                .andExpect(jsonPath("$.meta.totalPages").value(3))
                .andExpect(jsonPath("$.meta.hasNext").value(true))
                .andExpect(jsonPath("$.meta.hasPrevious").value(false))
        }

        @Test
        fun `Validation에 실패하면 400과 Field Error를 반환한다`() {
            mockMvc
                .perform(
                    post("/test/web/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"name":""}"""),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/test/web/validate"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("name은 필수입니다."))
                .andExpect(jsonPath("$.timestamp").value(matchesPattern(ISO_8601_PATTERN)))
        }

        @Test
        fun `잘못된 JSON 요청은 400 MALFORMED_JSON으로 처리된다`() {
            mockMvc
                .perform(
                    post("/test/web/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"name":"""),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"))
        }

        @Test
        fun `지원하지 않는 HTTP Method는 405로 처리된다`() {
            mockMvc
                .perform(delete("/test/web/success"))
                .andExpect(status().isMethodNotAllowed)
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
        }

        @Test
        fun `지원하지 않는 Media Type은 415로 처리된다`() {
            mockMvc
                .perform(
                    post("/test/web/validate")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("name=abc"),
                ).andExpect(status().isUnsupportedMediaType)
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
        }

        @Test
        fun `요청 값 형식이 올바르지 않으면 400 TYPE_MISMATCH로 처리된다`() {
            mockMvc
                .perform(get("/test/web/param").param("count", "not-a-number"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("TYPE_MISMATCH"))
        }

        @Test
        fun `필수 Query Parameter가 없으면 400 MISSING_REQUEST_PARAMETER로 처리된다`() {
            mockMvc
                .perform(get("/test/web/param"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("MISSING_REQUEST_PARAMETER"))
        }

        @Test
        fun `존재하지 않는 경로는 404 RESOURCE_NOT_FOUND로 처리된다`() {
            mockMvc
                .perform(get("/test/web/does-not-exist"))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
        }

        @Test
        fun `예상하지 못한 오류는 500으로 처리되며 내부 정보를 노출하지 않는다`() {
            mockMvc
                .perform(get("/test/web/error"))
                .andExpect(status().isInternalServerError)
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."))
                .andExpect(content().string(not(containsString("의도적으로 발생시킨"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("\tat "))))
        }

        companion object {
            private const val ISO_8601_PATTERN = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$"
        }
    }
