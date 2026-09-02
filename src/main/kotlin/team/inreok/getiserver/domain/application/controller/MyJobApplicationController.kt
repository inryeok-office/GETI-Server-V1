package team.inreok.getiserver.domain.application.controller

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
import team.inreok.getiserver.domain.application.dto.MyJobApplicationListResponse
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus
import team.inreok.getiserver.domain.application.service.JobApplicationService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

/**
 * 학생 본인 지원 목록이다(Issue #184). Student Web/App의 `my-application-list` 화면이 Mock 대신
 * 실제 API를 호출할 수 있도록, 기존 `domain.application` Service/Repository/DTO를 재사용해
 * `/api/v1/me/job-applications`를 새로 공개한다. `SecurityConfig`의 기존 "/api/v1/me/ 이하 모든
 * 경로" 규칙(인증 필수)이 이미 이 경로를 포함하므로 별도 Security 설정을 추가하지 않는다.
 */
@Tag(
    name = "Application - 내 지원 목록",
    description = "로그인한 학생 본인의 지원 목록을 조회한다(Issue #184). 필요 권한: 인증된 사용자(본인 지원서만 반환).",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
@RestController
@RequestMapping("/api/v1/me/job-applications")
class MyJobApplicationController(
    private val jobApplicationService: JobApplicationService,
) {
    @Operation(
        summary = "내 지원 목록 조회",
        description = """
            로그인한 학생 본인이 지원한 지원서 목록을 최신 등록순으로 반환한다. status를 지정하면
            그 상태만 필터하고(예: SUBMITTED), 지정하지 않으면 DRAFT(임시저장 중)를 포함한 모든
            상태를 반환한다 -- 담당 공고 여부와 무관하게 모든 교사·개발자가 조회하는 관리자 목록과
            달리, 본인의 임시저장은 이어서 작성할 수 있도록 본인에게는 그대로 노출한다. 기본
            page=0, size=20이다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공(결과가 없으면 빈 목록)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping
    fun list(
        authentication: Authentication,
        @Parameter(description = "지원 상태 필터(선택). 지정하지 않으면 모든 상태(DRAFT 포함)를 반환한다.")
        @RequestParam(required = false)
        status: JobApplicationStatus?,
        @Parameter(description = "Pagination(page: 0부터 시작, size: 기본 20)") pageable: Pageable,
    ): ApiResponse<MyJobApplicationListResponse> {
        val studentMemberId = authentication.principal as Long
        return ApiResponse.of(jobApplicationService.list(studentMemberId, status, pageable))
    }
}
