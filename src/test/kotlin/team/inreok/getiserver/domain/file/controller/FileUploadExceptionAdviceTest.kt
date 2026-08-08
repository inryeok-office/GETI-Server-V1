package team.inreok.getiserver.domain.file.controller

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.servlet.HandlerExceptionResolver
import team.inreok.getiserver.domain.file.service.FileDownloadService
import team.inreok.getiserver.domain.file.service.FileUploadService
import team.inreok.getiserver.global.security.JwtTokenProvider

/**
 * `spring.servlet.multipart.max-file-size` 초과가 실제로 `FILE_TOO_LARGE`로 응답되는지 본다.
 *
 * MockMvc의 `multipart()` 요청은 이미 `MockMultipartHttpServletRequest`라 `DispatcherServlet`이
 * Multipart를 다시 파싱하지 않는다. 즉 [FileControllerTest]로는 이 분기를 재현할 수 없어, 실제
 * 실패 지점인 "Handler가 아직 정해지지 않은 상태의 예외 처리"를 Resolver에 직접 물어본다.
 *
 * [FileUploadExceptionAdvice]에 `assignableTypes`를 다시 붙이면 이 Test가 깨진다 -- 그때는
 * Advice가 선택되지 않아 `GlobalExceptionHandler`가 `INVALID_REQUEST`로 응답한다.
 */
@WebMvcTest(controllers = [FileController::class])
class FileUploadExceptionAdviceTest
    @Autowired
    constructor(
        @param:Qualifier("handlerExceptionResolver")
        private val handlerExceptionResolver: HandlerExceptionResolver,
    ) {
        @MockitoBean
        private lateinit var fileUploadService: FileUploadService

        @MockitoBean
        private lateinit var fileDownloadService: FileDownloadService

        @MockitoBean
        private lateinit var jwtTokenProvider: JwtTokenProvider

        @Test
        fun `Handler가 정해지기 전에 끊긴 크기 초과도 413 FILE_TOO_LARGE로 응답한다`() {
            val request = MockHttpServletRequest("POST", "/api/v1/files")
            val response = MockHttpServletResponse()

            // handler = null이 이 Test의 핵심이다. DispatcherServlet.doDispatch()는
            // checkMultipart()를 getHandler()보다 먼저 호출하므로, 크기 초과가 감지되는 시점에는
            // 어떤 Controller가 처리할 요청인지 아직 정해져 있지 않다.
            val modelAndView =
                handlerExceptionResolver.resolveException(
                    request,
                    response,
                    null,
                    MaxUploadSizeExceededException(MAX_UPLOAD_SIZE_BYTES),
                )

            assertThat(modelAndView).describedAs("어떤 Handler도 예외를 처리하지 못했다").isNotNull()
            assertThat(response.status).isEqualTo(413)
            assertThat(response.contentAsString).contains("FILE_TOO_LARGE")
            assertThat(response.contentAsString).doesNotContain("INVALID_REQUEST")
        }

        private companion object {
            private const val MAX_UPLOAD_SIZE_BYTES = 20L * 1024 * 1024
        }
    }
