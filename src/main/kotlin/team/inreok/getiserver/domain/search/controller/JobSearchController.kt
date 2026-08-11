package team.inreok.getiserver.domain.search.controller

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
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.job.entity.type.PostingType
import team.inreok.getiserver.domain.search.dto.JobSearchResponse
import team.inreok.getiserver.domain.search.dto.JobSort
import team.inreok.getiserver.domain.search.dto.PublicJobStatus
import team.inreok.getiserver.domain.search.dto.SortDirection
import team.inreok.getiserver.domain.search.service.JobSearchService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

/**
 * `GET /api/v1/jobs`(공개 공고 검색, Issue #69)를 담당한다. 공고 상세/등록/수정은 여전히
 * `job` Module(`JobController`/`JobAdminController`)의 책임이다 — 목록/검색만 Elasticsearch
 * 기반으로 옮겨졌고, `job`이 이 결과를 다시 참조하지 않아(Job → Search 방향 의존 없음) Spring
 * Modulith가 요구하는 순환 없는 Module 경계를 유지한다.
 */
@Tag(
    name = "Job - 공고 조회",
    description = "공개된 채용 공고를 검색한다. 필요 권한: 학생, 교사, 개발자.",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
// SecurityConfig가 /api/v1/jobs 경로를 인증 필수로 지정하므로, 여기 도달했다는 것은 이미 유효한
// Access Token(JwtAuthenticationFilter)이 SecurityContext에 인증 정보를 채웠다는 뜻이다.
@RestController
@RequestMapping("/api/v1/jobs")
class JobSearchController(
    private val jobSearchService: JobSearchService,
) {
    @Operation(
        summary = "공개 공고 목록 조회",
        description = """
            공개된 공고를 Elasticsearch 기반으로 검색한다(Issue #69). 게시(PUBLISHED)되었거나
            마감(CLOSED)된 공고 중 삭제되지 않은 것만 반환하며, 임시저장(DRAFT)과 삭제(DELETED)
            공고는 이 API로 조회할 수 없다.

            `query`는 공고 제목·기업명·본문에 대한 다중 필드 검색어다(Nori 형태소 분석 기반).
            `techStackIds` 필터는 지원하지 않는다 — `jobs.required_skills`가 구조화된 ID 관계가
            아니라 자유 형식 JSONB라 정확한 필터를 구현할 수 없다(Issue #69 "문서 불일치 및
            설계 결정" 참고). `sourceId` 대신 `sourceName`(문자열)을 사용한다.

            정렬 기준은 `sort`, 방향은 `direction`으로 지정한다(Pagination Parameter의 `sort`는
            무시된다). 모든 정렬은 방향과 무관하게 동일 값에서 공고 ID 내림차순 보조 정렬로
            안정화되어 Page 사이 중복·누락이 발생하지 않는다.

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
        @Parameter(description = "제목·기업명·본문 검색어(선택). 생략하면 전체 조회", example = "백엔드")
        @RequestParam(required = false)
        query: String?,
        @Parameter(description = "공고 유형 필터(선택)")
        @RequestParam(required = false)
        postingType: PostingType?,
        @Parameter(description = "공고 상태 필터(선택). PUBLISHED 또는 CLOSED만 지정할 수 있다.")
        @RequestParam(required = false)
        status: PublicJobStatus?,
        @Parameter(description = "기업 유형 필터(선택)")
        @RequestParam(required = false)
        companyType: CompanyType?,
        @Parameter(description = "공고 출처 필터(선택). `jobs.source_name`과 정확히 일치해야 한다.", example = "MMA")
        @RequestParam(required = false)
        sourceName: String?,
        @Parameter(description = "지원 대상 학년 필터(선택, 1~3)", example = "3")
        @RequestParam(required = false)
        targetGrade: Int?,
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
        @Parameter(
            description =
                "정렬 방향(선택). 생략하면 sort별 기본 방향(LATEST/VIEWS는 내림차순, DEADLINE은 " +
                    "오름차순)을 사용한다.",
        )
        @RequestParam(required = false)
        direction: SortDirection?,
        @Parameter(description = "Pagination(page: 0부터 시작, size: 기본 20, 최대 100). sort Parameter는 무시된다")
        pageable: Pageable,
        authentication: Authentication,
    ): ApiResponse<JobSearchResponse> =
        ApiResponse.of(
            jobSearchService.search(
                query,
                postingType,
                status,
                companyType,
                sourceName,
                targetGrade,
                openOnly,
                sort,
                direction,
                pageable,
                authentication.principal as Long,
            ),
        )
}
