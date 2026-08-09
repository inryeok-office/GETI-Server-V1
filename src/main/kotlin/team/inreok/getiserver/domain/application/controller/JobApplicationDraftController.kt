package team.inreok.getiserver.domain.application.controller

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
import team.inreok.getiserver.domain.application.dto.JobApplicationDraftResponse
import team.inreok.getiserver.domain.application.dto.SaveJobApplicationDraftRequest
import team.inreok.getiserver.domain.application.service.JobApplicationService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(
    name = "Application - 지원서 임시저장",
    description =
        "학생 본인의 지원서를 임시저장한다(요구사항 9절). 필요 권한: 인증된 사용자(본인 소유 " +
            "지원서만). 요구사항 원문에 별도 Endpoint 계약이 없어 초안 생성과 같은 Resource를 " +
            "PATCH로 재사용하도록 설계했다(docs/application/application-domain-plan.md §6.6).",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
// SecurityConfig가 /api/v1/job-applications/**를 인증 필수로 지정하므로, 여기 도달했다는 것은
// 이미 유효한 Access Token으로 인증됐다는 뜻이다. 소유권·상태 검증은 Role로 알 수 없어
// JobApplicationService가 별도로 수행한다.
@RestController
@RequestMapping("/api/v1/job-applications")
class JobApplicationDraftController(
    private val jobApplicationService: JobApplicationService,
) {
    @Operation(
        summary = "지원서 임시저장",
        description = """
            전달한 Field만 반영한다(부분 수정). answers를 전달하면 기존 답변 전체를 그 값으로
            교체한다. 필수값이 비어 있는 상태로도 저장할 수 있고, 제출 일시는 만들지 않는다.
            DRAFT 상태의 본인 지원서에만 사용할 수 있다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "저장 성공"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "본인의 지원서가 아님 (APPLICATION_ACCESS_FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "404", description = "지원서가 없음 (APPLICATION_NOT_FOUND)"),
        SwaggerApiResponse(
            responseCode = "409",
            description = "DRAFT 상태가 아니어서 저장할 수 없음 (APPLICATION_ACTION_NOT_AVAILABLE)",
        ),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PatchMapping("/{applicationId}")
    fun saveDraft(
        authentication: Authentication,
        @Parameter(description = "임시저장할 지원서 ID", example = "1") @PathVariable applicationId: Long,
        @Valid @RequestBody request: SaveJobApplicationDraftRequest,
    ): ApiResponse<JobApplicationDraftResponse> {
        val studentMemberId = authentication.principal as Long
        return ApiResponse.of(jobApplicationService.saveDraft(applicationId, studentMemberId, request))
    }
}
