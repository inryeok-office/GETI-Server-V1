package team.inreok.getiserver.global.web

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [WebTestSupportController::class])
@TestPropertySource(
    properties = [
        "app.web.cors.allowed-origins=https://allowed.geti.local",
    ],
)
class WebCorsConfigTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @Test
        fun `허용된 Origin의 CORS Preflight 요청은 허용된다`() {
            mockMvc
                .perform(
                    options("/test/web/success")
                        .header(HttpHeaders.ORIGIN, "https://allowed.geti.local")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name()),
                ).andExpect(status().isOk)
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://allowed.geti.local"))
        }

        @Test
        fun `허용되지 않은 Origin의 CORS Preflight 요청은 거부된다`() {
            mockMvc
                .perform(
                    options("/test/web/success")
                        .header(HttpHeaders.ORIGIN, "https://not-allowed.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name()),
                ).andExpect(status().isForbidden)
        }
    }
