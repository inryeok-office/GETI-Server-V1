package team.inreok.getiserver.domain.portfolio.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.portfolio.dto.PortfolioRequestCreateRequest
import team.inreok.getiserver.domain.portfolio.dto.PortfolioRequestResponse
import team.inreok.getiserver.domain.portfolio.dto.PortfolioRequestStatusUpdateRequest
import team.inreok.getiserver.domain.portfolio.dto.PortfolioRequestUpdateRequest
import team.inreok.getiserver.domain.portfolio.service.PortfolioRequestService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(
    name = "Portfolio - 수합 요청 관리",
    description = "포트폴리오 수합 요청을 등록·수정하고 상태를 변경한다. 필요 권한: 교사, 개발자.",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
// SecurityConfig가 이 경로를 TEACHER 또는 DEVELOPER 권한 필수로 지정하므로, 여기 도달했다는 것은
// 이미 유효한 Access Token으로 인증되고 두 역할 중 하나를 가졌다는 뜻이다. 등록자 본인만 수정할 수
// 있는지(소유권)는 기준이 확정되지 않아 이번 범위에서 역할까지만 검증한다(Job 관리 API와 같은 관례).
@RestController
@RequestMapping("/api/v1/admin/portfolio-requests")
class PortfolioRequestAdminController(
    private val portfolioRequestService: PortfolioRequestService,
) {
    @Operation(
        summary = "수합 요청 등록",
        description = """
            포트폴리오 수합 요청을 DRAFT 상태로 등록한다. 공개(PUBLISHED)는 상태 변경 API를 사용한다.
            작성자는 Access Token에서 식별해 자동으로 기록한다.

            `targetStudentIds`는 최소 한 명 이상이어야 하고(TARGET_STUDENT_REQUIRED), 모두 실제
            학생(STUDENT)이어야 한다 -- 존재하지 않거나 학생이 아닌 id가 있으면
            INVALID_TARGET_STUDENT로 거부한다. 중복 id는 하나로 취급한다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "201", description = "등록 성공"),
        SwaggerApiResponse(
            responseCode = "400",
            description =
                "제목 공백·형식 오류(VALIDATION_FAILED), 대상 학생 미지정(TARGET_STUDENT_REQUIRED), " +
                    "존재하지 않거나 학생이 아닌 대상(INVALID_TARGET_STUDENT), 필수 Field 누락(MALFORMED_JSON)",
        ),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "교사 또는 개발자 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createRequest(
        @Valid @RequestBody request: PortfolioRequestCreateRequest,
        authentication: Authentication,
    ): ApiResponse<PortfolioRequestResponse> =
        ApiResponse.of(portfolioRequestService.create(request, authentication.principal as Long))

    @Operation(
        summary = "수합 요청 부분 수정",
        description = """
            전달한 Field만 수정하고, 생략(null)한 Field는 기존 값을 유지한다. 제목·설명·마감 시각은
            DRAFT/PUBLISHED에서 수정할 수 있다.

            대상 학생 교체(`targetStudentIds`)는 DRAFT에서만 허용한다 -- 이미 제출한 학생을 대상에서
            빼면 Submission이 고아가 되는 문제를 피하기 위한 기준이다. CLOSED/DELETED 요청은 어떤
            Field도 수정할 수 없다(PORTFOLIO_REQUEST_NOT_EDITABLE).
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "수정 성공"),
        SwaggerApiResponse(
            responseCode = "400",
            description =
                "제목 공백·형식 오류(VALIDATION_FAILED), 대상 학생 미지정(TARGET_STUDENT_REQUIRED), " +
                    "존재하지 않거나 학생이 아닌 대상(INVALID_TARGET_STUDENT)",
        ),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "교사 또는 개발자 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "404", description = "수합 요청이 없거나 삭제됨 (PORTFOLIO_REQUEST_NOT_FOUND)"),
        SwaggerApiResponse(
            responseCode = "409",
            description = "수정할 수 없는 상태이거나 DRAFT가 아닌데 대상 교체를 시도함 (PORTFOLIO_REQUEST_NOT_EDITABLE)",
        ),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PatchMapping("/{requestId}")
    fun updateRequest(
        @Parameter(description = "수정할 수합 요청 ID", example = "1") @PathVariable requestId: Long,
        @Valid @RequestBody request: PortfolioRequestUpdateRequest,
    ): ApiResponse<PortfolioRequestResponse> = ApiResponse.of(portfolioRequestService.update(requestId, request))

    @Operation(
        summary = "수합 요청 상태 변경",
        description = """
            수합 요청 상태를 변경한다. 허용 전이는 다음과 같고 그 외(동일 상태로의 변경 포함)는 409로
            거부한다.

            ```
            DRAFT     -> PUBLISHED | DELETED
            PUBLISHED -> CLOSED    | DELETED
            CLOSED    -> DELETED
            ```

            DRAFT->PUBLISHED(공개)는 대상 학생이 최소 한 명 있어야 한다(없으면 TARGET_STUDENT_REQUIRED).
            DELETED는 Soft Delete로 처리되어 실제 행이 보존된다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "상태 변경 성공"),
        SwaggerApiResponse(responseCode = "400", description = "공개하려는데 대상 학생이 없음 (TARGET_STUDENT_REQUIRED)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "교사 또는 개발자 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "404", description = "수합 요청이 없거나 삭제됨 (PORTFOLIO_REQUEST_NOT_FOUND)"),
        SwaggerApiResponse(responseCode = "409", description = "허용되지 않은 상태 전이 (PORTFOLIO_STATUS_TRANSITION_INVALID)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PatchMapping("/{requestId}/status")
    fun changeStatus(
        @Parameter(description = "상태를 변경할 수합 요청 ID", example = "1") @PathVariable requestId: Long,
        @Valid @RequestBody request: PortfolioRequestStatusUpdateRequest,
    ): ApiResponse<PortfolioRequestResponse> = ApiResponse.of(portfolioRequestService.changeStatus(requestId, request))
}
