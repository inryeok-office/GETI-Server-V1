package team.inreok.getiserver.global.web

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [WebTestSupportController::class])
class RequestIdFilterTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @Test
        fun `Client가 requestId를 보내지 않으면 서버가 생성해 Header와 Body에 담는다`() {
            mockMvc
                .perform(get("/test/web/success"))
                .andExpect(status().isOk)
                .andExpect(header().exists(RequestIdFilter.REQUEST_ID_HEADER))
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty)
        }

        @Test
        fun `Client가 보낸 X-Request-Id를 새로 만들지 않고 그대로 재사용한다`() {
            mockMvc
                .perform(get("/test/web/success").header(RequestIdFilter.REQUEST_ID_HEADER, "test-request-id-1234"))
                .andExpect(status().isOk)
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, "test-request-id-1234"))
                .andExpect(jsonPath("$.meta.requestId").value("test-request-id-1234"))
        }

        @Test
        fun `오류 응답도 같은 X-Request-Id를 Header와 Body에 담는다`() {
            mockMvc
                .perform(get("/test/web/error").header(RequestIdFilter.REQUEST_ID_HEADER, "test-request-id-5678"))
                .andExpect(status().isInternalServerError)
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, "test-request-id-5678"))
                .andExpect(jsonPath("$.meta.requestId").value("test-request-id-5678"))
        }
    }
