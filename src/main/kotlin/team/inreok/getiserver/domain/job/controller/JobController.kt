package team.inreok.getiserver.domain.job.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.job.dto.JobDetailResponse
import team.inreok.getiserver.domain.job.service.JobService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(
    name = "Job - 공고 조회",
    description = "공개된 채용 공고 상세 정보를 조회한다. 필요 권한: 학생, 교사, 개발자.",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
// SecurityConfig가 이 경로를 인증 필수로 지정하므로, 여기 도달했다는 것은 이미 유효한
// Access Token(JwtAuthenticationFilter)이 SecurityContext에 인증 정보를 채웠다는 뜻이다.
//
// 공개 목록/검색(GET /api/v1/jobs, 목록 조회는 이 Class에 없음)은 domain.search.controller의
// JobSearchController가 같은 경로(/api/v1/jobs)를 담당한다. Elasticsearch 기반 검색은 job
// 원본을 읽어야 하고(search → job), job이 검색 결과를 다시 그 Interface로 되돌려 받으면
// (job → search) 순환 의존이 생겨 Spring Modulith가 이를 거부한다(Issue #69, ModularityTest).
// 그래서 목록/검색의 소유권 자체를 search로 옮겼다 — job은 상세 조회와 등록/수정/상태 변경만
// 남는다.
@RestController
@RequestMapping("/api/v1/jobs")
class JobController(
    private val jobService: JobService,
) {
    @Operation(
        summary = "공개 공고 상세 조회",
        description = """
            jobId로 지정한 공고의 상세 정보를 조회하고 조회수를 1 증가시킨다. 동일 사용자나 동일
            IP의 중복 조회도 현재 정책상 모두 증가시킨다. 응답의 `viewCount`에는 이번 조회분이
            반영된 값이 담긴다.

            존재하지 않거나 삭제된 공고는 404, 아직 게시되지 않은(DRAFT) 공고는 403으로 처리한다.
            임시저장 공고를 확인하려면 관리자용 상세 조회 API를 사용한다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "아직 공개되지 않은 공고 (JOB_NOT_VISIBLE)"),
        SwaggerApiResponse(responseCode = "404", description = "공고가 없거나 삭제됨 (JOB_NOT_FOUND)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping("/{jobId}")
    fun getJob(
        @Parameter(description = "조회할 공고 ID", example = "1") @PathVariable jobId: Long,
    ): ApiResponse<JobDetailResponse> = ApiResponse.of(jobService.getPublicDetail(jobId))
}
