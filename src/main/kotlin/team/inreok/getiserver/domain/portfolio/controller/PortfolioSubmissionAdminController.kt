package team.inreok.getiserver.domain.portfolio.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.portfolio.dto.PortfolioSubmissionStatusListResponse
import team.inreok.getiserver.domain.portfolio.service.PortfolioSubmissionAdminService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import java.io.OutputStream
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

/**
 * 관리자(교사·개발자)가 수합 요청의 제출 현황을 조회하고 제출 자료를 일괄 다운로드한다(요구사항
 * §19/§24, Issue #204 Phase 2b). `/api/v1/admin/portfolio-requests` 하위 경로는 `SecurityConfig`가
 * 이미 TEACHER 또는 DEVELOPER 역할 필수로 지정하므로 여기 도달했다는 것은 두 역할 중 하나로
 * 인증됐다는 뜻이다. 등록자 본인 소유권까지는 검증하지 않는다(PortfolioRequestAdminController와 같은 관례).
 */
@Tag(
    name = "Portfolio - 관리자 제출 현황",
    description = "교사·개발자가 수합 요청의 제출 현황을 조회하고 제출 자료를 ZIP으로 일괄 다운로드한다. 필요 권한: 교사, 개발자.",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
@RestController
@RequestMapping("/api/v1/admin/portfolio-requests")
class PortfolioSubmissionAdminController(
    private val portfolioSubmissionAdminService: PortfolioSubmissionAdminService,
) {
    @Operation(
        summary = "제출 현황 목록 조회",
        description = """
            수합 요청의 제출 대상 학생 전체를 기준으로 제출 현황을 조회한다. 아직 제출하지 않은 학생도
            "미제출"(submitted=false, status/materialType null)로 포함한다. 제출 완료(SUBMITTED)만
            제출로 세고 임시저장(DRAFT)은 미제출로 취급한다.

            `submitted`를 지정하면 제출 완료 여부로 좁히고, `name`을 지정하면 학생 이름으로 좁힌다
            (부분 일치, 대소문자 무시). 이름 오름차순으로 정렬하며 기본 page=0, size=20이다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공(결과가 없으면 빈 목록)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "교사 또는 개발자 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "404", description = "수합 요청이 없거나 삭제됨 (PORTFOLIO_REQUEST_NOT_FOUND)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping("/{requestId}/submissions")
    fun listSubmissions(
        @Parameter(description = "제출 현황을 조회할 수합 요청 ID", example = "1") @PathVariable requestId: Long,
        @Parameter(description = "제출 완료 여부 필터(선택). true면 제출 완료, false면 미제출만 조회한다.")
        @RequestParam(required = false)
        submitted: Boolean?,
        @Parameter(description = "학생 이름 검색어(선택, 부분 일치·대소문자 무시)")
        @RequestParam(required = false)
        name: String?,
        @Parameter(description = "Pagination(page: 0부터 시작, size: 기본 20)") pageable: Pageable,
    ): ApiResponse<PortfolioSubmissionStatusListResponse> =
        ApiResponse.of(
            portfolioSubmissionAdminService.getSubmissionStatuses(
                requestId = requestId,
                submitted = submitted,
                name = name,
                pageable = pageable,
            ),
        )

    @Operation(
        summary = "제출 자료 일괄 다운로드",
        description = """
            수합 요청의 학생 제출물에 연결된 파일을 모아 하나의 ZIP으로 내려받는다. 각 파일 이름에는
            학생을 구분할 수 있는 memberId·이름이 포함된다. `submittedOnly=true`면 제출 완료(SUBMITTED)
            제출물의 파일만 담고, 기본값(false)이면 임시저장(DRAFT)의 파일도 함께 담는다.

            내려받을 파일이 하나도 없으면 404(NO_SUBMISSIONS_TO_EXPORT)로 처리하고 빈 ZIP을 내려주지
            않는다. 응답 Content-Type은 application/zip이고 Content-Disposition으로 파일명을 지정한다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "ZIP 생성 성공(application/zip Binary)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "교사 또는 개발자 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(
            responseCode = "404",
            description =
                "수합 요청이 없거나 삭제됨(PORTFOLIO_REQUEST_NOT_FOUND), 내려받을 제출 자료가 없음(NO_SUBMISSIONS_TO_EXPORT)",
        ),
        SwaggerApiResponse(
            responseCode = "413",
            description = "일괄 다운로드 허용 개수 또는 총 용량을 초과함 (FILE_ARCHIVE_TOO_LARGE)",
        ),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping("/{requestId}/submissions/export")
    fun exportSubmissions(
        @Parameter(description = "제출 자료를 내려받을 수합 요청 ID", example = "1") @PathVariable requestId: Long,
        @Parameter(description = "제출 완료(SUBMITTED) 제출물만 포함할지 여부(선택, 기본 false)")
        @RequestParam(required = false, defaultValue = "false")
        submittedOnly: Boolean,
        response: HttpServletResponse,
    ) {
        // DB 조회(존재·빈 목록 판정 포함)를 먼저 끝내 여기서 예외가 나면 정상적인 JSON 오류 응답으로
        // 나가게 한다 -- 이 시점까지는 response에 아무것도 쓰지 않았다.
        val entries = portfolioSubmissionAdminService.buildExportEntries(requestId, submittedOnly)

        // 실제로 ZIP Byte를 쓰기 시작하는 순간에만 Header를 설정한다. writeZip 내부의 개수·용량 상한
        // 검증(Storage 접근 전)이 예외를 던질 수 있는데, 미리 설정한 Header는 GlobalExceptionHandler가
        // response.reset() 없이 오류 응답을 쓰기 때문에 남아버린다(브라우저가 JSON 오류를 ZIP
        // 첨부파일로 내려받음). Byte를 쓰기 직전으로 Header 설정을 미뤄 이 경로에서 오류 응답이
        // 오염되지 않게 한다(JobApplicationExportController와 동일한 이유).
        val deferredOutput =
            HeaderDeferringOutputStream(response.outputStream) {
                response.contentType = "application/zip"
                response.setHeader(
                    "Content-Disposition",
                    "attachment; filename=\"portfolio-request-$requestId-submissions.zip\"",
                )
            }
        portfolioSubmissionAdminService.writeZip(entries, deferredOutput)
    }

    /**
     * 실제로 Byte를 쓰기 직전에만 [onFirstWrite]를 실행하는 [OutputStream] Decorator다
     * (JobApplicationExportController와 동일한 패턴). ZIP 생성 성공·부분 실패 경로는 Header가 설정된
     * 채 진행되고, Storage에 접근하지 못한 검증 실패 경로(개수·용량 상한, 빈 목록)는 Header를 건드리지
     * 않는다.
     */
    private class HeaderDeferringOutputStream(
        private val delegate: OutputStream,
        private val onFirstWrite: () -> Unit,
    ) : OutputStream() {
        private var headersApplied = false

        private fun applyHeadersOnce() {
            if (headersApplied) return
            onFirstWrite()
            headersApplied = true
        }

        override fun write(b: Int) {
            applyHeadersOnce()
            delegate.write(b)
        }

        override fun write(b: ByteArray) {
            applyHeadersOnce()
            delegate.write(b)
        }

        override fun write(
            b: ByteArray,
            off: Int,
            len: Int,
        ) {
            applyHeadersOnce()
            delegate.write(b, off, len)
        }

        override fun flush() = delegate.flush()

        override fun close() = delegate.close()
    }
}
