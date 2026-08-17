package team.inreok.getiserver.domain.application.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.application.service.JobApplicationExportService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

/**
 * 공고별 지원자 자료 일괄 다운로드다(Application Phase 10, Issue #138). 응답이 `application/zip`
 * Binary라 다른 Controller처럼 [team.inreok.getiserver.global.web.ApiResponse]로 감싸지 않는다 --
 * [HttpServletResponse]에 직접 쓴다. `/api/v1/admin/jobs` 하위 전체 경로는 `SecurityConfig`가
 * 이미 TEACHER 또는 DEVELOPER 역할 필수로 지정하므로 별도 설정 추가가 필요 없다.
 */
@Tag(
    name = "Application - 지원자 자료 일괄 다운로드",
    description = "교사·개발자가 한 공고의 지원자 제출 자료(Snapshot 기준 첨부파일)를 ZIP으로 한 번에 내려받는다(Issue #138).",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
@RestController
@RequestMapping("/api/v1/admin/jobs")
class JobApplicationExportController(
    private val jobApplicationExportService: JobApplicationExportService,
) {
    @Operation(
        summary = "공고별 지원자 자료 일괄 다운로드",
        description = """
            대상 공고의 등록자·담당 교사·개발자만 호출할 수 있다. 지원자마다 가장 최근 제출
            Snapshot(재제출로 임시저장 중인 값이 아니라 실제로 SUBMIT/RESUBMIT된 시점의 답변)에
            담긴 FILE 유형 답변의 첨부파일을 모아 `{지원자 이름}_{원본 파일명}` 형태의 항목으로
            ZIP에 담는다. 응답 Content-Type은 application/zip이고 Content-Disposition으로
            내려받을 파일명을 지정한다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "ZIP 생성 성공(application/zip Binary)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(
            responseCode = "403",
            description =
                "교사·개발자가 아님(FORBIDDEN), 해당 공고의 등록자·담당 교사·개발자가 아님(APPLICATION_REVIEW_FORBIDDEN)",
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "공고가 없음(JOB_NOT_FOUND), 내려받을 첨부파일이 없음 (FILE_ARCHIVE_EMPTY)",
        ),
        SwaggerApiResponse(
            responseCode = "413",
            description = "일괄 다운로드 허용 개수 또는 총 용량을 초과함 (FILE_ARCHIVE_TOO_LARGE)",
        ),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping("/{jobId}/applications/export")
    fun exportApplications(
        authentication: Authentication,
        @Parameter(description = "대상 공고 ID", example = "1") @PathVariable jobId: Long,
        response: HttpServletResponse,
    ) {
        val requesterMemberId = authentication.principal as Long
        val isDeveloper = authentication.authorities.any { it.authority == "ROLE_DEVELOPER" }

        // DB 조회(권한 판정 포함)를 먼저 끝내 여기서 예외가 나면 정상적인 JSON 오류 응답으로
        // 나가게 한다 -- 이 시점까지는 response에 아무것도 쓰지 않았다. writeZip 내부에서
        // 개수·용량 상한을 넘겨 예외가 나는 경로는 이미 응답이 시작된 뒤일 수 있어 그 경우
        // 다운로드가 잘릴 수 있다(FileArchivePort.writeZip KDoc에 문서화된 한계와 같은 종류).
        val entries = jobApplicationExportService.buildExportEntries(jobId, requesterMemberId, isDeveloper)

        response.contentType = "application/zip"
        response.setHeader("Content-Disposition", "attachment; filename=\"job-$jobId-applications.zip\"")
        jobApplicationExportService.writeZip(entries, response.outputStream)
    }
}
