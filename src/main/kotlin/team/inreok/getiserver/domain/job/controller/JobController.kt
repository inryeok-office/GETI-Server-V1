package team.inreok.getiserver.domain.job.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.job.dto.JobDetailResponse
import team.inreok.getiserver.domain.job.dto.JobSort
import team.inreok.getiserver.domain.job.dto.JobSummaryResponse
import team.inreok.getiserver.domain.job.dto.PublicJobStatus
import team.inreok.getiserver.domain.job.entity.type.PostingType
import team.inreok.getiserver.domain.job.service.JobService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import team.inreok.getiserver.global.web.PageResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(
    name = "Job - 공고 조회",
    description = "공개된 채용 공고를 검색하고 상세 정보를 조회한다. 필요 권한: 학생, 교사, 개발자.",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
// SecurityConfig가 이 경로를 인증 필수로 지정하므로, 여기 도달했다는 것은 이미 유효한
// Access Token(JwtAuthenticationFilter)이 SecurityContext에 인증 정보를 채웠다는 뜻이다.
@RestController
@RequestMapping("/api/v1/jobs")
class JobController(
    private val jobService: JobService,
) {
    @Operation(
        summary = "공개 공고 목록 조회",
        description = """
            공개된 공고를 검색한다. 게시(PUBLISHED)되었거나 마감(CLOSED)된 공고 중 삭제되지 않은
            것만 반환하며, 임시저장(DRAFT)과 삭제(DELETED) 공고는 이 API로 조회할 수 없다.

            `query`는 공고 제목 부분 일치(대소문자 무시)다. 기업명 검색은 이번 범위에서 제외되어
            있다. `techStackIds`, `companyType`, `sourceId` 필터도 지원하지 않는다.

            정렬은 `sort`로만 지정한다(Pagination Parameter의 `sort`는 무시된다). 모든 정렬은
            동일 값에서 공고 ID 내림차순으로 안정화되어 Page 사이 중복·누락이 발생하지 않는다.

            목록 기본값은 page=0, size=20이며 최대 size=100이다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공(결과가 없으면 빈 목록)"),
        SwaggerApiResponse(
            responseCode = "400",
            description = "status 또는 sort에 허용되지 않은 값을 보냄 (TYPE_MISMATCH)",
        ),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping
    fun searchJobs(
        @Parameter(description = "공고 제목 검색어(부분 일치, 선택). 생략하면 전체 조회", example = "백엔드")
        @RequestParam(required = false)
        query: String?,
        @Parameter(description = "공고 유형 필터(선택)")
        @RequestParam(required = false)
        postingType: PostingType?,
        @Parameter(description = "공고 상태 필터(선택). PUBLISHED 또는 CLOSED만 지정할 수 있다.")
        @RequestParam(required = false)
        status: PublicJobStatus?,
        @Parameter(
            description =
                "true면 마감되지 않은 게시 공고만 반환한다. 모집 종료 시각이 없는 공고는 " +
                    "마감 없는 공고로 보아 계속 포함된다.",
            example = "false",
        )
        @RequestParam(defaultValue = "false")
        openOnly: Boolean,
        @Parameter(description = "정렬 기준(기본 LATEST). 최근 게시순/마감 임박순/조회수순")
        @RequestParam(defaultValue = "LATEST")
        sort: JobSort,
        @Parameter(description = "Pagination(page: 0부터 시작, size: 기본 20, 최대 100). sort Parameter는 무시된다")
        pageable: Pageable,
    ): ApiResponse<PageResponse<JobSummaryResponse>> =
        ApiResponse.of(jobService.searchPublic(query, postingType, status, openOnly, sort, pageable))

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
