package team.inreok.getiserver.domain.application.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.application.dto.JobApplicationFormLinkRequest
import team.inreok.getiserver.domain.application.dto.JobApplicationFormLinkResponse
import team.inreok.getiserver.domain.application.service.JobApplicationFormLinkService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(
    name = "Application - 공고 양식 연결",
    description =
        "교내 지원(INTERNAL) 공고에 본인 소유의 활성 신청 양식을 연결한다. 필요 권한: 교사, 개발자. " +
            "담당자 검증(등록자·담당 교사)은 개발자만 우회할 수 있고, 양식 소유권 검증은 역할과 " +
            "무관하게 항상 적용된다(요구사항 원문에 Endpoint 계약이 없어 이번 PR에서 새로 설계, " +
            "docs/application/application-domain-plan.md §6.4 참고).",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
// SecurityConfig가 이 경로를 TEACHER 또는 DEVELOPER 역할 필수로 지정하므로, 여기 도달했다는 것은
// 이미 유효한 Access Token으로 인증되고 두 역할 중 하나를 가졌다는 뜻이다. 담당자·양식 소유권
// 검증은 Role만으로는 알 수 없어 JobApplicationFormLinkService가 별도로 수행한다.
@RestController
@RequestMapping("/api/v1/admin/jobs/{jobId}/application-form")
class JobApplicationFormLinkController(
    private val jobApplicationFormLinkService: JobApplicationFormLinkService,
) {
    @Operation(
        summary = "공고-양식 연결",
        description = """
            공고에 활성 양식을 연결(또는 재연결)한다. 공고당 활성 양식은 하나만 존재하며,
            이미 연결되어 있으면 새 요청으로 덮어쓴다. 연결하려는 양식은 호출자 본인 소유의
            JOB 유형·ACTIVE 상태여야 한다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "연결(또는 재연결) 성공"),
        SwaggerApiResponse(
            responseCode = "400",
            description =
                "INTERNAL 지원 방식이 아님(JOB_APPLICATION_METHOD_NOT_INTERNAL), " +
                    "JOB 유형이 아닌 양식(INVALID_FORM_FIELD), ACTIVE 상태가 아닌 양식(FORM_NOT_ACTIVE)",
        ),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(
            responseCode = "403",
            description =
                "교사·개발자가 아님(FORBIDDEN), 공고 등록자·담당교사·개발자가 아님(JOB_MANAGE_FORBIDDEN), " +
                    "본인 소유 양식이 아님(FORM_NOT_OWNED)",
        ),
        SwaggerApiResponse(responseCode = "404", description = "공고가 없음(JOB_NOT_FOUND), 양식이 없음(FORM_NOT_FOUND)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PostMapping
    fun linkForm(
        authentication: Authentication,
        @Parameter(description = "대상 공고 ID", example = "1") @PathVariable jobId: Long,
        @Valid @RequestBody request: JobApplicationFormLinkRequest,
    ): ApiResponse<JobApplicationFormLinkResponse> {
        val requesterMemberId = authentication.principal as Long
        val isDeveloper = authentication.authorities.any { it.authority == "ROLE_DEVELOPER" }
        return ApiResponse.of(jobApplicationFormLinkService.link(jobId, requesterMemberId, isDeveloper, request))
    }
}
