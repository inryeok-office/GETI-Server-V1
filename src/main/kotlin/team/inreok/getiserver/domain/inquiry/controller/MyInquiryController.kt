package team.inreok.getiserver.domain.inquiry.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.inquiry.dto.InquiryListResponse
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryStatus
import team.inreok.getiserver.domain.inquiry.service.InquiryService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(
    name = "Inquiry - 내 문의 목록",
    description = "현재 로그인한 사용자 본인이 작성한 문의 목록을 조회한다. 필요 권한: 학생, 교사, 개발자.",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
// SecurityConfig가 "/api/v1/me/inquiries"를 명시적으로 authenticated로 선언한다(기존
// "/api/v1/me/**" 규칙과 값은 같지만, 그 규칙에 암묵적으로 기대지 않기 위해 별도로 선언했다 --
// SecurityConfig Class 주석 참고). 항상 인증 주체(authentication.principal) 기준으로만 조회하고
// memberId를 Query Parameter로 받지 않는다(요구사항 §13 "memberId를 받지 않는다").
@RestController
@RequestMapping("/api/v1/me/inquiries")
class MyInquiryController(
    private val inquiryService: InquiryService,
) {
    @Operation(
        summary = "내 문의 목록 조회",
        description = """
            현재 로그인한 사용자가 작성한 문의만 최신 등록순으로 조회한다. `status`를 지정하면 해당
            상태의 문의만 반환하고, 지정하지 않으면 전체 상태를 반환한다. 기본 page=0, size=20이며
            최대 size=100이다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공(결과가 없으면 빈 목록)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping
    fun listMyInquiries(
        authentication: Authentication,
        @Parameter(description = "문의 상태 필터(선택)") @RequestParam(required = false) status: InquiryStatus?,
        @Parameter(description = "Pagination(page: 0부터 시작, size: 기본 20, 최대 100)") pageable: Pageable,
    ): ApiResponse<InquiryListResponse> {
        val memberId = authentication.principal as Long
        return ApiResponse.of(inquiryService.listMine(status, memberId, pageable))
    }
}
