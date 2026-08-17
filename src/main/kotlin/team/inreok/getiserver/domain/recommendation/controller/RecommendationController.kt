package team.inreok.getiserver.domain.recommendation.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.recommendation.dto.RecommendationListResponse
import team.inreok.getiserver.domain.recommendation.dto.RecommendationSettingRequest
import team.inreok.getiserver.domain.recommendation.dto.RecommendationSettingResponse
import team.inreok.getiserver.domain.recommendation.service.RecommendationService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(
    name = "Recommendation - 맞춤 공고 추천",
    description =
        "학생 본인의 맞춤 공고 추천을 조회하고, 추천 기능 ON/OFF와 관심 없음을 관리한다(Recommendation " +
            "R3, Issue #152). Recommendation Score 자체는 R2 Core(Issue #148)가 매일 계산해 저장한 " +
            "결과를 그대로 읽는다 — 이 API가 호출될 때마다 다시 계산하지 않는다. 필요 권한: STUDENT.",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
// SecurityConfig가 /api/v1/recommendations 이하를 STUDENT Role 필수로 지정하므로, 여기 도달했다는
// 것은 이미 학생으로 인증됐다는 뜻이다. 조회·설정 대상을 항상 요청자 본인으로 한정하는 것은 Role로
// 알 수 없어 memberId를 Request Body/Path가 아닌 인증 Principal에서만 가져온다.
@RestController
@RequestMapping("/api/v1/recommendations")
class RecommendationController(
    private val recommendationService: RecommendationService,
) {
    @Operation(
        summary = "내 추천 조회",
        description = """
            요청자 본인의 오늘자 맞춤 공고 추천을 조회한다. `status`로 상태를 구분한다.

            - DISABLED: 추천 기능이 꺼져 있다(설정한 적 없는 회원 포함). `items`는 항상 빈 배열이고
              `generatedAt`은 항상 null이다 — 기존 추천 결과가 있어도 노출하지 않는다.
            - EMPTY: 추천 기능은 켜져 있지만 오늘자 추천 결과가 없다.
            - READY: 오늘자 추천 결과가 1건 이상 있다. `items`는 rank 오름차순으로 정렬된다.

            GENERATING/FAILED는 R4 Daily Scheduler가 추가되면 사용할 값으로 예약만 되어 있고 이
            API는 아직 반환하지 않는다. 결과가 없어도(DISABLED/EMPTY) 오류가 아니라 200이다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공(DISABLED/EMPTY도 200)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "학생 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping
    fun getMyRecommendations(authentication: Authentication): ApiResponse<RecommendationListResponse> {
        val memberId = authentication.principal as Long
        return ApiResponse.of(recommendationService.getMyRecommendations(memberId))
    }

    @Operation(
        summary = "추천 기능 ON/OFF 설정",
        description = "요청자 본인의 추천 기능을 켜거나 끈다. 이전 값과 같은 값을 다시 보내도 오류 없이 성공한다(멱등).",
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "설정 반영 성공"),
        SwaggerApiResponse(responseCode = "400", description = "요청 값 형식 오류(enabled 누락 등) (VALIDATION_FAILED)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "학생 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PatchMapping("/settings")
    fun updateSetting(
        authentication: Authentication,
        @RequestBody request: RecommendationSettingRequest,
    ): ApiResponse<RecommendationSettingResponse> {
        val memberId = authentication.principal as Long
        return ApiResponse.of(recommendationService.updateSetting(memberId, request.enabled))
    }

    @Operation(
        summary = "공고 관심 없음 설정",
        description = """
            요청자 본인에게 해당 공고를 관심 없음으로 표시한다. 이후 추천 계산(R4 Daily Scheduler)에서
            이 공고를 다시 추천하지 않으며, 오늘자 추천 목록에 이미 있었다면 즉시 목록에서 사라진다.
            이미 관심 없음 상태인 공고를 다시 요청해도 오류 없이 성공한다(멱등).
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "204", description = "설정 성공(응답 본문 없음, 이미 설정된 경우 포함)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "학생 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "404", description = "공고가 없거나 삭제됨 (JOB_NOT_FOUND)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PostMapping("/{jobId}/not-interested")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markNotInterested(
        authentication: Authentication,
        @Parameter(description = "관심 없음으로 설정할 공고 ID", example = "1") @PathVariable jobId: Long,
    ) {
        val memberId = authentication.principal as Long
        recommendationService.markNotInterested(memberId, jobId)
    }

    @Operation(
        summary = "공고 관심 없음 해제",
        description = """
            요청자 본인의 관심 없음 설정을 해제한다. 해제한다고 즉시 추천이 다시 생성되거나 과거
            추천 결과가 복원되지는 않는다 — 다음 R4 Daily Scheduler 실행부터 다시 후보가 될 수 있다.
            이미 해제된(또는 애초에 설정한 적 없는) 공고를 다시 요청해도 오류 없이 성공한다(멱등).
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "204", description = "해제 성공(응답 본문 없음, 이미 해제된 경우 포함)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "학생 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @DeleteMapping("/{jobId}/not-interested")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeNotInterested(
        authentication: Authentication,
        @Parameter(description = "관심 없음을 해제할 공고 ID", example = "1") @PathVariable jobId: Long,
    ) {
        val memberId = authentication.principal as Long
        recommendationService.removeNotInterested(memberId, jobId)
    }
}
