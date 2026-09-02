package team.inreok.getiserver.domain.portfolio.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.portfolio.dto.PortfolioSubmissionResponse
import team.inreok.getiserver.domain.portfolio.dto.PortfolioSubmissionUpsertRequest
import team.inreok.getiserver.domain.portfolio.service.PortfolioSubmissionService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(
    name = "Portfolio - 학생 제출",
    description = "학생이 자신의 포트폴리오를 임시저장하거나 제출한다. 필요 권한: 학생.",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
// SecurityConfig가 /api/v1/portfolio-requests/**를 인증 필수로 지정하므로, 여기 도달했다는 것은 이미
// 유효한 Access Token으로 인증됐다는 뜻이다. "요청자가 이 요청의 제출 대상인가"는 Role만으로 알 수
// 없어 PortfolioSubmissionService가 대상 여부와 제출 가능 시점을 별도로 판정한다.
@RestController
@RequestMapping("/api/v1/portfolio-requests")
class PortfolioSubmissionController(
    private val portfolioSubmissionService: PortfolioSubmissionService,
) {
    @Operation(
        summary = "포트폴리오 제출/임시저장",
        description = """
            학생이 지정된 수합 요청에 자신의 포트폴리오를 제출하거나 임시저장한다. 요청·학생 조합의
            제출물은 하나뿐이라, 전달한 값이 그대로 최종 상태가 되는 Upsert다(부분 수정이 아니다).

            `status=DRAFT`는 임시저장, `status=SUBMITTED`는 제출 확정이다. `fileIds`는 이 제출물에
            연결할 파일의 최종 목록이며, 본인이 업로드한 PORTFOLIO 용도 파일만 연결할 수 있다 --
            그렇지 않은 파일이 있으면 File 도메인 규칙에 따라 거부된다(FILE_NOT_OWNED 등).

            요청자가 대상이 아니면 403(NOT_REQUEST_TARGET), 아직 공개되지 않았거나 없거나 삭제된
            요청은 404(PORTFOLIO_REQUEST_NOT_FOUND), 이미 마감(CLOSED)됐거나 마감 시각을 지난
            요청은 409(SUBMISSION_CLOSED)로 처리한다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "제출/임시저장 성공"),
        SwaggerApiResponse(
            responseCode = "400",
            description =
                "형식 오류(VALIDATION_FAILED), 연결할 파일이 없거나 상태가 잘못됨(FILE_NOT_FOUND), " +
                    "용도 불일치(FILE_PURPOSE_MISMATCH), 개수 초과(FILE_COUNT_EXCEEDED)",
        ),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(
            responseCode = "403",
            description = "제출 대상이 아니거나 본인 소유가 아닌 파일을 연결함 (NOT_REQUEST_TARGET, FILE_NOT_OWNED)",
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "요청이 없거나 삭제됨, 또는 학생에게 아직 공개되지 않음 (PORTFOLIO_REQUEST_NOT_FOUND)",
        ),
        SwaggerApiResponse(
            responseCode = "409",
            description = "제출 기간이 아니거나 마감됨(SUBMISSION_CLOSED), 이미 연결된 파일(FILE_ALREADY_LINKED)",
        ),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PatchMapping("/{requestId}/submission")
    fun submit(
        @Parameter(description = "제출할 수합 요청 ID", example = "1") @PathVariable requestId: Long,
        @Valid @RequestBody request: PortfolioSubmissionUpsertRequest,
        authentication: Authentication,
    ): ApiResponse<PortfolioSubmissionResponse> =
        ApiResponse.of(
            portfolioSubmissionService.submit(
                requestId = requestId,
                requesterId = authentication.principal as Long,
                request = request,
            ),
        )
}
