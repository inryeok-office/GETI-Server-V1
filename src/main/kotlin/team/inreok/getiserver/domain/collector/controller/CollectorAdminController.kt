package team.inreok.getiserver.domain.collector.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.collector.dto.CollectionRunDetailResponse
import team.inreok.getiserver.domain.collector.dto.CollectionRunListResponse
import team.inreok.getiserver.domain.collector.dto.CollectorActionRequest
import team.inreok.getiserver.domain.collector.dto.CollectorActionResponse
import team.inreok.getiserver.domain.collector.dto.JobSourceListResponse
import team.inreok.getiserver.domain.collector.dto.JobSourceUpdateRequest
import team.inreok.getiserver.domain.collector.dto.JobSourceUpdateResponse
import team.inreok.getiserver.domain.collector.entity.type.CollectionRunStatus
import team.inreok.getiserver.domain.collector.service.CollectionRunQueryService
import team.inreok.getiserver.domain.collector.service.CollectorExecutionService
import team.inreok.getiserver.domain.collector.service.JobSourceService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import java.time.LocalDateTime
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(
    name = "Collector - 수집 운영",
    description = "외부 채용 공고 수집원과 수집 실행 이력을 관리한다. 필요 권한: 개발자.",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
// SecurityConfig가 이 경로를 DEVELOPER 권한 필수로 지정하므로, 여기 도달했다는 것은 이미 유효한
// Access Token으로 인증되고 DEVELOPER 역할을 가졌다는 뜻이다.
@RestController
@RequestMapping("/api/v1/admin")
class CollectorAdminController(
    private val jobSourceService: JobSourceService,
    private val collectionRunQueryService: CollectionRunQueryService,
    private val collectorExecutionService: CollectorExecutionService,
) {
    @Operation(
        summary = "수집원 설정·상태 목록 조회",
        description = """
            등록된 모든 수집원(MMA, JOB_ALIO, CLEAN_EYE, NARA_ILTEO, IBK_ONE_JOB, SARAMIN, WORK24)의
            설정과 상태를 반환한다. `configured`는 DB에 저장된 값이 아니라 실행 시점에 Provider
            구현 존재 여부와 인증 설정 상태로 계산한다. 인증키 원문은 응답에 포함하지 않는다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "개발자 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping("/job-sources")
    fun listJobSources(): ApiResponse<JobSourceListResponse> = ApiResponse.of(jobSourceService.list())

    @Operation(
        summary = "수집원 활성 상태 변경",
        description = """
            수집원의 활성화 여부를 변경한다. 활성화(`enabled=true`)는 승인 상태가 READY이고,
            설정이 완료(`configured=true`)되어 있고, 해당 수집원의 실행이 진행 중이 아닐 때만
            허용한다. 비활성화는 제한 없이 항상 허용한다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "변경 성공"),
        SwaggerApiResponse(responseCode = "400", description = "요청 값 형식 오류(VALIDATION_FAILED)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "개발자 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "404", description = "수집원이 없음 (JOB_SOURCE_NOT_FOUND)"),
        SwaggerApiResponse(
            responseCode = "409",
            description =
                "승인되지 않음(SOURCE_NOT_APPROVED), 설정되지 않음(SOURCE_NOT_CONFIGURED), " +
                    "이미 실행 중(COLLECTOR_ALREADY_RUNNING)",
        ),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PatchMapping("/job-sources/{sourceId}")
    fun updateJobSource(
        @Parameter(description = "변경할 수집원 ID", example = "1") @PathVariable sourceId: Long,
        @Valid @RequestBody request: JobSourceUpdateRequest,
    ): ApiResponse<JobSourceUpdateResponse> = ApiResponse.of(jobSourceService.updateEnabled(sourceId, request))

    @Operation(
        summary = "수동 수집·동기화 실행",
        description = """
            지정한 수집원을 수동으로 즉시 실행한다. 대상 수집원 중 하나라도 승인·설정·중복 실행
            조건을 만족하지 못하면 어떤 실행도 만들지 않고 즉시 오류를 반환한다(부분 접수 없음).

            요청 Thread는 실제 외부 수집이 끝나기 전에 즉시 반환한다(202 Accepted). 생성된 각
            수집 실행은 PENDING 상태로 즉시 만들어지고, 실제 Provider 호출과 결과 반영은 별도
            Thread에서 비동기로 진행되며 `GET /api/v1/admin/collection-runs/{runId}`로 진행
            상황을 확인한다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "202", description = "접수 성공"),
        SwaggerApiResponse(responseCode = "400", description = "요청 값 형식 오류(VALIDATION_FAILED)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "개발자 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "404", description = "수집원이 없음 (JOB_SOURCE_NOT_FOUND)"),
        SwaggerApiResponse(
            responseCode = "409",
            description =
                "승인되지 않음(SOURCE_NOT_APPROVED), 설정되지 않음(SOURCE_NOT_CONFIGURED), " +
                    "이미 실행 중(COLLECTOR_ALREADY_RUNNING)",
        ),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PostMapping("/collector-actions")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun triggerCollectorAction(
        @Valid @RequestBody request: CollectorActionRequest,
    ): ApiResponse<CollectorActionResponse> {
        val acceptedAt = LocalDateTime.now()
        val runs =
            collectorExecutionService.triggerManual(
                requireNotNull(request.action),
                requireNotNull(request.sourceIds),
            )
        return ApiResponse.of(
            CollectorActionResponse(
                runIds = runs.map { requireNotNull(it.id) },
                status = CollectionRunStatus.PENDING,
                acceptedAt = acceptedAt,
            ),
        )
    }

    @Operation(
        summary = "수집 실행 목록 조회",
        description = """
            수집 실행 이력을 조건으로 검색한다. 모든 필터는 선택이며 DB Pagination으로 처리한다.
            목록 기본값은 page=0, size=20이며 최대 size=100이다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공(결과가 없으면 빈 목록)"),
        SwaggerApiResponse(responseCode = "400", description = "status에 허용되지 않은 값을 보냄 (TYPE_MISMATCH)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "개발자 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping("/collection-runs")
    fun searchCollectionRuns(
        @Parameter(description = "수집원 ID 필터(선택)") @RequestParam(required = false) sourceId: Long?,
        @Parameter(description = "실행 상태 필터(선택)") @RequestParam(required = false) status: CollectionRunStatus?,
        @Parameter(description = "조회 시작 시각(선택, startedAt >= startAt)") @RequestParam(required = false) startAt:
            LocalDateTime?,
        @Parameter(description = "조회 종료 시각(선택, startedAt <= endAt)") @RequestParam(required = false) endAt:
            LocalDateTime?,
        @Parameter(description = "Pagination(page: 0부터 시작, size: 기본 20, 최대 100)") pageable: Pageable,
    ): ApiResponse<CollectionRunListResponse> =
        ApiResponse.of(collectionRunQueryService.search(sourceId, status, startAt, endAt, pageable))

    @Operation(
        summary = "수집 실행 상세 조회",
        description = "runId로 지정한 수집 실행의 집계와 오류 목록을 조회한다.",
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "개발자 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "404", description = "수집 실행이 없음 (COLLECTION_RUN_NOT_FOUND)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping("/collection-runs/{runId}")
    fun getCollectionRun(
        @Parameter(description = "조회할 수집 실행 ID", example = "1") @PathVariable runId: Long,
    ): ApiResponse<CollectionRunDetailResponse> = ApiResponse.of(collectionRunQueryService.getDetail(runId))
}
