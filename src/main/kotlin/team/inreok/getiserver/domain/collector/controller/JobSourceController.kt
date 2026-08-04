package team.inreok.getiserver.domain.collector.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.collector.dto.PublicJobSourceListResponse
import team.inreok.getiserver.domain.collector.service.JobSourceService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(
    name = "Job - 공고 출처",
    description = "공개된 채용 공고 출처 목록을 조회한다. 필요 권한: 학생, 교사, 개발자.",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
// SecurityConfig가 이 경로를 인증 필수로 지정하므로, 여기 도달했다는 것은 이미 유효한 Access
// Token으로 인증됐다는 뜻이다. 관리자 전용 상세 정보(승인 상태, configured, 인증키 관련 필드)는
// GET /api/v1/admin/job-sources(CollectorAdminController)만 제공한다.
@RestController
@RequestMapping("/api/v1/job-sources")
class JobSourceController(
    private val jobSourceService: JobSourceService,
) {
    @Operation(
        summary = "공개 공고 출처 목록 조회",
        description = "채용 공고 출처(수집원) 목록을 최소 정보로 조회한다. 인증키·승인 상태 등 운영 정보는 포함하지 않는다.",
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping
    fun listJobSources(
        @Parameter(description = "true면 활성화된 출처만 반환한다.", example = "false")
        @RequestParam(defaultValue = "false")
        activeOnly: Boolean,
    ): ApiResponse<PublicJobSourceListResponse> = ApiResponse.of(jobSourceService.listPublic(activeOnly))
}
