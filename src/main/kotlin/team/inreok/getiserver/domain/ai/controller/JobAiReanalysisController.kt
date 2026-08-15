package team.inreok.getiserver.domain.ai.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.ai.dto.AiReanalysisResponse
import team.inreok.getiserver.domain.ai.service.AiAnalysisService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(
    name = "Job - AI 분석",
    description = "게시된 채용 공고의 AI 분석 결과를 수동으로 재요청한다. 필요 권한: 학생, 교사, 개발자.",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
// SecurityConfig의 "/api/v1/jobs/**"가 이미 인증을 요구하므로 별도 Role 제한을 추가하지 않는다
// (GETI Notion AI Analysis Phase 1 권한: STUDENT/TEACHER/DEVELOPER = 현재 모든 내부 Role).
@RestController
@RequestMapping("/api/v1/jobs")
class JobAiReanalysisController(
    private val aiAnalysisService: AiAnalysisService,
) {
    @Operation(
        summary = "AI 공고 분석 수동 재요청",
        description = """
            지정한 공고의 AI 분석을 다시 요청한다. 요청 Thread는 실제 OpenAI 호출이 끝나기 전에
            즉시 반환한다(202 Accepted) -- 실제 분석 결과는 이후 `GET /api/v1/jobs/{jobId}`의
            `aiAnalysis`로 확인한다.

            대상 공고가 없거나 삭제됐거나 아직 게시(PUBLISHED)되지 않았으면 404, 이미 분석이
            진행 중이면 409, 수동 재분석 가능 횟수(최대 3회)를 모두 사용했으면 429를 반환한다.
            OpenAI 연동이 설정되지 않아 분석을 수행할 수 없으면 재분석 횟수를 소비하지 않고
            503을 반환한다. `canReanalyze`/`remainingReanalysisCount`는 서버가 계산해 응답에
            포함하므로 클라이언트가 직접 횟수를 계산하지 않아도 된다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "202", description = "재분석 접수 성공"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "404", description = "게시된 공고를 찾을 수 없음 (JOB_NOT_FOUND)"),
        SwaggerApiResponse(responseCode = "409", description = "이미 AI 분석이 진행 중 (AI_ALREADY_PROCESSING)"),
        SwaggerApiResponse(responseCode = "429", description = "수동 재분석 가능 횟수 초과 (AI_REANALYSIS_LIMIT)"),
        SwaggerApiResponse(responseCode = "503", description = "AI 분석 기능을 사용할 수 없음 (AI_PROVIDER_UNAVAILABLE)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PostMapping("/{jobId}/ai-reanalysis")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun reanalyze(
        @Parameter(description = "재분석할 공고 ID", example = "1") @PathVariable jobId: Long,
    ): ApiResponse<AiReanalysisResponse> = ApiResponse.of(aiAnalysisService.reanalyze(jobId))
}
