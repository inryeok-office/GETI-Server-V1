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
import team.inreok.getiserver.domain.recommendation.dto.RecommendationExclusionCreateRequest
import team.inreok.getiserver.domain.recommendation.dto.RecommendationExclusionListResponse
import team.inreok.getiserver.domain.recommendation.dto.RecommendationExclusionResponse
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
 * URL이 두 갈래(`/api/v1/me/job-recommendations`, `/api/v1/me/recommendation-exclusions`,
 * `/api/v1/recommendations/settings`)로 나뉘어 있어 Class 단위 `@RequestMapping` 대신 Method마다
 * 전체 경로를 직접 쓴다(`DiscordDeliveryAdminController`와 같은 관례). `settings`는 Notion API
 * 명세 전체에서 대응하는 별도 Endpoint를 찾지 못해 기존 경로를 그대로 유지했다(PR 본문
 * CONTRACT_MISMATCH 참고, 근거 없이 새 Path를 만들지 않는다). 필요 권한: STUDENT.
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

            `SIMILAR_JOBS`는 이 값 자체를 저장·조회하는 것만 지원한다 -- 유사 공고를 함께 제외하는
            확장 알고리즘은 확정된 정의가 없어 이번 범위에 포함하지 않았다(PR 본문 DECISION_REQUIRED
            참고).
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "201", description = "등록 성공"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "학생 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "404", description = "공고가 없거나 삭제됨 (JOB_NOT_FOUND)"),
        SwaggerApiResponse(responseCode = "409", description = "이미 관심 없음으로 설정됨 (EXCLUSION_ALREADY_EXISTS)"),
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
}
