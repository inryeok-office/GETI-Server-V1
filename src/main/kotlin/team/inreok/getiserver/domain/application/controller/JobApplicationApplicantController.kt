package team.inreok.getiserver.domain.application.controller

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
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.application.dto.JobApplicationApplicantListResponse
import team.inreok.getiserver.domain.application.service.JobApplicationApplicantService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

/**
 * 공고별 공개 신청자 목록이다(Application Phase 9, Issue #137). `/api/v1/admin/jobs` 하위 전체
 * 경로는 `SecurityConfig`가 이미 TEACHER 또는 DEVELOPER 역할 필수로 지정하므로 별도 설정 추가가
 * 필요 없다. 상태 변경 담당자 검증과 같은 이유로 대상 공고의 등록자·담당 교사 여부는 Role만으로
 * 알 수 없어 `JobApplicationApplicantService`가 별도로 수행한다.
 */
@Tag(
    name = "Application - 공고별 지원자 목록",
    description = "교사·개발자가 한 공고에 지원한 지원자 목록을 최소 정보로 조회한다(Issue #137).",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
@RestController
@RequestMapping("/api/v1/admin/jobs")
class JobApplicationApplicantController(
    private val jobApplicationApplicantService: JobApplicationApplicantService,
) {
    @Operation(
        summary = "공고별 지원자 목록 조회",
        description = """
            대상 공고의 등록자·담당 교사·개발자만 호출할 수 있다. 제출된(SUBMITTED 이후, DRAFT·
            WITHDRAWN 제외) 지원서를 최신 등록순으로 반환한다. 각 항목은 지원서 ID와 지원자의
            이름·프로필 이미지 URL·기수·학과만 담는다(최소 정보 노출 원칙, 지원 상태는 포함하지
            않음). 기본 page=0, size=20이다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공(결과가 없으면 빈 목록)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(
            responseCode = "403",
            description =
                "교사·개발자가 아님(FORBIDDEN), 해당 공고의 등록자·담당 교사·개발자가 아님(APPLICATION_REVIEW_FORBIDDEN)",
        ),
        SwaggerApiResponse(responseCode = "404", description = "공고가 없음 (JOB_NOT_FOUND)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping("/{jobId}/applicants")
    fun listApplicants(
        authentication: Authentication,
        @Parameter(description = "대상 공고 ID", example = "1") @PathVariable jobId: Long,
        @Parameter(description = "Pagination(page: 0부터 시작, size: 기본 20)") pageable: Pageable,
    ): ApiResponse<JobApplicationApplicantListResponse> {
        val requesterMemberId = authentication.principal as Long
        val isDeveloper = authentication.authorities.any { it.authority == "ROLE_DEVELOPER" }
        return ApiResponse.of(
            jobApplicationApplicantService.list(jobId, requesterMemberId, isDeveloper, pageable),
        )
    }
}
