package team.inreok.getiserver.domain.portfolio.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.portfolio.dto.PortfolioRequestListResponse
import team.inreok.getiserver.domain.portfolio.dto.PortfolioRequestResponse
import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioRequestStatus
import team.inreok.getiserver.domain.portfolio.service.PortfolioRequestService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(
    name = "Portfolio - 수합 요청 조회",
    description = "포트폴리오 수합 요청 목록·상세를 조회한다. 필요 권한: 학생, 교사, 개발자.",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
// SecurityConfig가 이 경로를 인증 필수로 지정하므로, 여기 도달했다는 것은 이미 유효한 Access Token이
// SecurityContext에 인증 정보를 채웠다는 뜻이다. 학생은 자신이 대상인 공개 이후 요청만 볼 수 있는데,
// 이 "대상 여부/역할 구분"은 Role만으로 알 수 없어 PortfolioRequestService가 별도로 판정한다.
@RestController
@RequestMapping("/api/v1/portfolio-requests")
class PortfolioRequestController(
    private val portfolioRequestService: PortfolioRequestService,
) {
    @Operation(
        summary = "수합 요청 목록 조회",
        description = """
            수합 요청 목록을 최신 등록순으로 조회한다. 교사·개발자는 관리 가능한 전체 요청을, 학생은
            자신이 대상인 공개 이후(PUBLISHED/CLOSED) 요청만 본다 -- 학생에게 자신이 대상이 아닌
            요청이나 아직 공개되지 않은 요청은 노출하지 않는다.

            `status`를 지정하면 해당 상태만 반환한다. 기본 page=0, size=20이다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공(결과가 없으면 빈 목록)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping
    fun listRequests(
        @Parameter(description = "상태 필터(선택)") @RequestParam(required = false) status: PortfolioRequestStatus?,
        @Parameter(description = "Pagination(page: 0부터 시작, size: 기본 20)") pageable: Pageable,
        authentication: Authentication,
    ): ApiResponse<PortfolioRequestListResponse> =
        ApiResponse.of(
            portfolioRequestService.getList(
                status = status,
                requesterId = authentication.principal as Long,
                isAdmin = authentication.isAdmin(),
                pageable = pageable,
            ),
        )

    @Operation(
        summary = "수합 요청 상세 조회",
        description = """
            requestId로 지정한 수합 요청의 상세 정보를 조회한다. 학생은 자신이 대상인 공개 이후 요청만
            조회할 수 있다 -- 대상이 아니면 403(NOT_REQUEST_TARGET), 아직 공개 전(DRAFT)이거나
            없거나 삭제된 요청은 404(PORTFOLIO_REQUEST_NOT_FOUND)로 처리한다. 교사·개발자는 모든
            상태(DRAFT 포함)의 요청을 조회할 수 있다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "학생이 대상이 아닌 요청을 조회함 (NOT_REQUEST_TARGET)"),
        SwaggerApiResponse(
            responseCode = "404",
            description = "요청이 없거나 삭제됨, 또는 학생에게 아직 공개되지 않음 (PORTFOLIO_REQUEST_NOT_FOUND)",
        ),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping("/{requestId}")
    fun getRequest(
        @Parameter(description = "조회할 수합 요청 ID", example = "1") @PathVariable requestId: Long,
        authentication: Authentication,
    ): ApiResponse<PortfolioRequestResponse> =
        ApiResponse.of(
            portfolioRequestService.getDetail(
                requestId = requestId,
                requesterId = authentication.principal as Long,
                isAdmin = authentication.isAdmin(),
            ),
        )
}

/** 요청자가 관리자(교사·개발자)인지 판정한다. 학생은 대상인 공개 이후 요청만 볼 수 있다. */
private fun Authentication.isAdmin(): Boolean =
    authorities.any { it.authority == "ROLE_TEACHER" || it.authority == "ROLE_DEVELOPER" }
