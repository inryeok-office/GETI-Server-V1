package team.inreok.getiserver.global.web

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.global.error.BusinessException
import team.inreok.getiserver.global.error.CommonErrorCode

/**
 * 공통 Web 기반(성공/오류 응답, 전역 예외 처리, Pagination, CORS, requestId)을 검증하기 위한
 * Test 전용 Controller다. `src/test`에만 존재하며 Production Source에는 두지 않는다.
 */
@RestController
class WebTestSupportController {
    @GetMapping("/test/web/success")
    fun success(): ApiResponse<Map<String, String>> = ApiResponse.of(mapOf("id" to "1"))

    @PostMapping("/test/web/validate")
    fun validate(
        @Valid @RequestBody request: WebTestRequest,
    ): ApiResponse<WebTestRequest> = ApiResponse.of(request)

    @GetMapping("/test/web/param")
    fun param(
        @RequestParam count: Int,
    ): ApiResponse<Int> = ApiResponse.of(count)

    @GetMapping("/test/web/error")
    fun error(): ApiResponse<Unit> = throw IllegalStateException("의도적으로 발생시킨 테스트 전용 오류")

    @GetMapping("/test/web/business-error")
    fun businessError(): ApiResponse<Unit> =
        throw BusinessException(CommonErrorCode.INVALID_REQUEST, "의도적으로 발생시킨 테스트 전용 Business 예외")

    @GetMapping("/test/web/page")
    fun page(): PageResponse<String> = PageResponse.of(PageImpl(listOf("a", "b"), PageRequest.of(0, 2), 5))

    @GetMapping("/test/web/pageable")
    fun pageable(pageable: Pageable): ApiResponse<Map<String, Int>> =
        ApiResponse.of(mapOf("page" to pageable.pageNumber, "size" to pageable.pageSize))
}

data class WebTestRequest(
    @field:NotBlank(message = "name은 필수입니다.")
    val name: String,
)
