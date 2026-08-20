package team.inreok.getiserver.domain.recommendation.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.job.entity.type.PostingType
import team.inreok.getiserver.domain.job.query.JobBookmarkQuery
import team.inreok.getiserver.domain.job.query.JobBookmarkSort
import team.inreok.getiserver.domain.recommendation.dto.BookmarkCreateRequest
import team.inreok.getiserver.domain.recommendation.dto.RecommendationExclusionCreateRequest
import team.inreok.getiserver.domain.recommendation.dto.RecommendationExclusionListResponse
import team.inreok.getiserver.domain.recommendation.dto.RecommendationExclusionResponse
import team.inreok.getiserver.domain.recommendation.dto.RecommendationJobListResponse
import team.inreok.getiserver.domain.recommendation.dto.RecommendationJobResponse
import team.inreok.getiserver.domain.recommendation.dto.RecommendationListResponse
import team.inreok.getiserver.domain.recommendation.dto.RecommendationSettingRequest
import team.inreok.getiserver.domain.recommendation.dto.RecommendationSettingResponse
import team.inreok.getiserver.domain.recommendation.entity.type.ExclusionType
import team.inreok.getiserver.domain.recommendation.entity.type.SuitabilityLevel
import team.inreok.getiserver.domain.recommendation.service.RecommendationService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

/**
 * 학생 본인의 맞춤 공고 추천을 조회하고, 추천 기능 ON/OFF와 관심 없음(추천 제외)을 관리한다(R3
 * Issue #152, Notion 계약 정합성 Issue #155). Recommendation Score 자체는 R2 Core(Issue #148)가
 * 매일 계산해 저장한 결과를 그대로 읽는다 -- 이 API가 호출될 때마다 다시 계산하지 않는다.
 *
 * URL이 여러 갈래(`/api/v1/me/job-recommendations`, `/api/v1/me/recommendation-exclusions`,
 * `/api/v1/recommendations/settings`, `/api/v1/me/bookmarks`)로 나뉘어 있어 Class 단위
 * `@RequestMapping` 대신 Method마다 전체 경로를 직접 쓴다(`DiscordDeliveryAdminController`와 같은
 * 관례). `settings`는 Notion API 명세 전체에서 대응하는 별도 Endpoint를 찾지 못해 기존 경로를
 * 그대로 유지했다(PR 본문 CONTRACT_MISMATCH 참고, 근거 없이 새 Path를 만들지 않는다).
 * `/api/v1/me/bookmarks`(Issue #170)는 확정된 Notion Path를 이 저장소에서 확인하지 못해
 * `/api/v1/me/recommendation-exclusions`(같은 Entity를 쓰는 가장 가까운 기존 계약)와 같은
 * `/api/v1/me/{resource}` Naming 관례를 따랐다(PR 본문 DECISION_REQUIRED 참고). 필요 권한: STUDENT.
 */
@Tag(
    name = "Recommendation - 맞춤 공고 추천",
    description =
        "학생 본인의 맞춤 공고 추천을 조회하고, 추천 기능 ON/OFF와 관심 없음을 관리한다. Recommendation " +
            "Score 자체는 R2 Core(Issue #148)가 매일 계산해 저장한 결과를 그대로 읽는다 -- 이 API가 호출될 " +
            "때마다 다시 계산하지 않는다. 필요 권한: STUDENT.",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
// SecurityConfig가 /api/v1/me/job-recommendations, /api/v1/me/recommendation-exclusions/**,
// /api/v1/recommendations/** 이하를 STUDENT Role 필수로 지정하므로, 여기 도달했다는 것은 이미
// 학생으로 인증됐다는 뜻이다. 조회·설정 대상을 항상 요청자 본인으로 한정하는 것은 Role로 알 수
// 없어 memberId를 Request Body/Path가 아닌 인증 Principal에서만 가져온다.
@RestController
class RecommendationController(
    private val recommendationService: RecommendationService,
) {
    @Operation(
        summary = "내 북마크 공고 목록 조회",
        description =
            "인증된 요청자 본인이 북마크한 공개 공고를 조회한다. 학생·교사·개발자 모두 사용할 수 있다. " +
                "query는 제목·기업명·본문을 검색하고, postingType과 companyType으로 필터링한다. sort는 " +
                "LATEST(최근 게시순), DEADLINE(마감 임박순), VIEWS(조회수순)이며 Pagination은 page=0, size=20 " +
                "기본값과 최대 size=100을 따른다. 결과가 없으면 200과 빈 content를 반환한다.",
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공(결과가 없으면 빈 목록)"),
        SwaggerApiResponse(responseCode = "400", description = "Query Enum 또는 Pagination 값이 잘못됨 (TYPE_MISMATCH)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping("/api/v1/me/job-bookmarks")
    fun listBookmarkedJobs(
        authentication: Authentication,
        @Parameter(description = "공고 제목·기업명·본문 검색어(선택)", example = "백엔드")
        @RequestParam(required = false)
        query: String?,
        @Parameter(description = "공고 유형 필터(선택)")
        @RequestParam(required = false)
        postingType: PostingType?,
        @Parameter(description = "기업 유형 필터(선택)")
        @RequestParam(required = false)
        companyType: CompanyType?,
        @Parameter(description = "정렬 기준(기본 LATEST): 최근 게시순, 마감 임박순, 조회수순")
        @RequestParam(defaultValue = "LATEST")
        sort: JobBookmarkSort,
        @Parameter(description = "Pagination(page: 0부터 시작, size: 기본 20, 최대 100). Pageable의 sort는 무시된다.")
        pageable: Pageable,
    ): ApiResponse<RecommendationJobListResponse> {
        val memberId = authentication.principal as Long
        return ApiResponse.of(
            recommendationService.listBookmarkedJobs(
                memberId,
                JobBookmarkQuery(query, postingType, companyType?.name, sort),
                pageable,
            ),
        )
    }

    @Operation(
        summary = "오늘의 추천 공고 조회",
        description = """
            요청자 본인의 오늘자 맞춤 공고 추천을 조회한다. `status`로 상태를 구분한다.

            - DISABLED: 추천 기능이 꺼져 있다(설정한 적 없는 회원 포함). `content`는 항상 빈 배열이고
              `generatedAt`은 항상 null이다 -- 기존 추천 결과가 있어도 노출하지 않는다.
            - GENERATING: Daily Scheduler가 이 회원의 추천을 계산하는 중이다. `content`는 빈 배열이다.
            - FAILED: Daily Scheduler의 마지막 계산이 실패했다. `content`는 빈 배열이다.
            - EMPTY: 추천 기능은 켜져 있고 마지막 계산은 성공했지만 오늘자 추천 결과가 없다.
            - READY: 오늘자 추천 결과가 1건 이상 있다. `content`는 rank 오름차순으로 정렬된다.

            `suitabilityLevel`로 필터링할 수 있다. `nextGenerationAt`은 Daily Scheduler 실행 시각이
            운영 환경에 명시적으로 설정되기 전까지 항상 null이다. 목록 기본값은 page=0, size=20이며
            최대 size=100이다. 결과가 없어도(DISABLED/EMPTY/GENERATING/FAILED) 오류가 아니라 200이다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공(DISABLED/EMPTY도 200)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "학생 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping("/api/v1/me/job-recommendations")
    fun getMyRecommendations(
        authentication: Authentication,
        @Parameter(description = "적합도 등급 필터(선택)")
        @RequestParam(required = false)
        suitabilityLevel: SuitabilityLevel?,
        @Parameter(description = "Pagination(page: 0부터 시작, size: 기본 20, 최대 100).")
        pageable: Pageable,
    ): ApiResponse<RecommendationListResponse> {
        val memberId = authentication.principal as Long
        return ApiResponse.of(recommendationService.getMyRecommendations(memberId, suitabilityLevel, pageable))
    }

    @Operation(
        summary = "추천 기능 ON/OFF 설정",
        description = """
            요청자 본인의 추천 기능을 켜거나 끈다. 이전 값과 같은 값을 다시 보내도 오류 없이 성공한다
            (멱등). 재학 중(`AcademicStatus.ENROLLED`)이 아닌 회원(졸업·자퇴)은 설정을 변경할 수 없다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "설정 반영 성공"),
        SwaggerApiResponse(
            responseCode = "400",
            description = "값 형식 오류(enabled 누락 등) 또는 재학 중이 아님 (VALIDATION_FAILED, NOT_ENROLLED)",
        ),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "학생 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PatchMapping("/api/v1/recommendations/settings")
    fun updateSetting(
        authentication: Authentication,
        @RequestBody request: RecommendationSettingRequest,
    ): ApiResponse<RecommendationSettingResponse> {
        val memberId = authentication.principal as Long
        return ApiResponse.of(recommendationService.updateSetting(memberId, request.enabled))
    }

    @Operation(
        summary = "관심 없음(추천 제외) 등록",
        description = """
            요청자 본인에게 해당 공고를 관심 없음으로 표시한다(`THIS_JOB` 또는 `SIMILAR_JOBS`). 이후
            추천 계산(R4 Daily Scheduler)에서 이 공고를 다시 추천하지 않으며, 오늘자 추천 목록에 이미
            있었다면 즉시 목록에서 사라진다. 같은 공고에 이미 관심 없음이 설정돼 있으면 409다(멱등이
            아니다 -- 다시 설정하려면 먼저 해제해야 한다).

            `SIMILAR_JOBS`는 기준 공고 자체뿐 아니라 유사하다고 판정되는 다른 공고도 함께
            제외한다(R6, Issue #167). 두 공고의 필수/우대 기술 스택을 합친 Set의 Jaccard
            Similarity가 Threshold(기본 0.6) 이상이면 유사로 판정한다 -- 등록 즉시 오늘자 추천
            목록에서도 유사 판정되는 공고가 함께 사라진다. 기준 공고의 AI 분석이 없으면(또는
            완료되지 않았으면) 기준 공고 자체만 제외되고 다른 공고로 확장하지 않는다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "201", description = "등록 성공"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "학생 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "404", description = "공고가 없거나 삭제됨 (JOB_NOT_FOUND)"),
        SwaggerApiResponse(
            responseCode = "409",
            description =
                "이미 관심 없음으로 설정됨 (EXCLUSION_ALREADY_EXISTS), 또는 북마크 등록과 동시에 도착해 " +
                    "경합함 -- 재시도 가능 (PREFERENCE_WRITE_CONFLICT)",
        ),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PostMapping("/api/v1/me/recommendation-exclusions")
    @ResponseStatus(HttpStatus.CREATED)
    fun registerExclusion(
        authentication: Authentication,
        @RequestBody request: RecommendationExclusionCreateRequest,
    ): ApiResponse<RecommendationExclusionResponse> {
        val memberId = authentication.principal as Long
        return ApiResponse.of(
            recommendationService.registerExclusion(memberId, request.jobId, request.exclusionType),
        )
    }

    @Operation(
        summary = "관심 없음(추천 제외) 목록 조회",
        description = """
            요청자 본인의 관심 없음 목록을 조회한다. `exclusionType`으로 필터링할 수 있고, 최신 설정
            순으로 정렬된다. 목록 기본값은 page=0, size=20이며 최대 size=100이다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공(결과가 없으면 빈 목록)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "학생 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping("/api/v1/me/recommendation-exclusions")
    fun listExclusions(
        authentication: Authentication,
        @Parameter(description = "관심 없음 종류 필터(선택)")
        @RequestParam(required = false)
        exclusionType: ExclusionType?,
        @Parameter(description = "Pagination(page: 0부터 시작, size: 기본 20, 최대 100).")
        pageable: Pageable,
    ): ApiResponse<RecommendationExclusionListResponse> {
        val memberId = authentication.principal as Long
        return ApiResponse.of(recommendationService.listExclusions(memberId, exclusionType, pageable))
    }

    @Operation(
        summary = "관심 없음(추천 제외) 해제",
        description = """
            요청자 본인의 관심 없음 설정을 해제한다. 해제한다고 즉시 추천이 다시 생성되거나 과거
            추천 결과가 복원되지는 않는다 -- 다음 R4 Daily Scheduler 실행부터 다시 후보가 될 수 있다.
            대상이 없거나 다른 사용자 소유면 404다(소유 여부를 노출하지 않는다).
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "204", description = "해제 성공(응답 본문 없음)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "학생 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "404", description = "관심 없음 설정을 찾을 수 없음(다른 사용자 소유 포함) (EXCLUSION_NOT_FOUND)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @DeleteMapping("/api/v1/me/recommendation-exclusions/{exclusionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeExclusion(
        authentication: Authentication,
        @Parameter(description = "해제할 관심 없음 Resource ID", example = "1") @PathVariable exclusionId: Long,
    ) {
        val memberId = authentication.principal as Long
        recommendationService.removeExclusion(memberId, exclusionId)
    }

    @Operation(
        summary = "공고 북마크 등록",
        description = """
            요청자 본인이 해당 공고를 북마크한다(Issue #170). 이미 북마크한 공고를 다시 등록하면
            409다(멱등이 아니다 -- 다시 등록하려면 먼저 해제해야 한다, 관심 없음 등록과 동일한 정책).
            관심 없음(exclusion) 설정이 이미 있는 공고라도 그 값은 그대로 유지되고 북마크만 함께
            표시된다.

            북마크한 공고는 추천 후보에서 제외되므로(기존 Hard Filter 정책), 등록 즉시 오늘자
            추천 목록에 있었다면 그 자리에서 사라진다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "201", description = "등록 성공"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "학생 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "404", description = "공고가 없거나 삭제됨 (JOB_NOT_FOUND)"),
        SwaggerApiResponse(
            responseCode = "409",
            description =
                "이미 북마크한 공고 (BOOKMARK_ALREADY_EXISTS), 또는 관심 없음 등록과 동시에 도착해 " +
                    "경합함 -- 재시도 가능 (PREFERENCE_WRITE_CONFLICT)",
        ),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PostMapping("/api/v1/me/bookmarks")
    @ResponseStatus(HttpStatus.CREATED)
    fun registerBookmark(
        authentication: Authentication,
        @RequestBody request: BookmarkCreateRequest,
    ): ApiResponse<RecommendationJobResponse> {
        val memberId = authentication.principal as Long
        return ApiResponse.of(recommendationService.registerBookmark(memberId, request.jobId))
    }

    @Operation(
        summary = "공고 북마크 해제",
        description = """
            요청자 본인의 공고 북마크를 해제한다(Issue #170). 해제한다고 즉시 추천이 다시
            생성되거나 과거 추천 결과가 복원되지는 않는다 -- 다음 R4 Daily Scheduler 실행부터 다시
            후보가 될 수 있다. 북마크하지 않은 공고를 해제하려 하면 404다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "204", description = "해제 성공(응답 본문 없음)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "학생 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "404", description = "북마크하지 않은 공고 (BOOKMARK_NOT_FOUND)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @DeleteMapping("/api/v1/me/bookmarks/{jobId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeBookmark(
        authentication: Authentication,
        @Parameter(description = "북마크를 해제할 공고 ID", example = "1") @PathVariable jobId: Long,
    ) {
        val memberId = authentication.principal as Long
        recommendationService.removeBookmark(memberId, jobId)
    }
}
