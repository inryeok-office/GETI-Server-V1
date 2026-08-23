package team.inreok.getiserver.domain.system.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.operation.entity.type.OperationJobType
import team.inreok.getiserver.domain.system.dto.OperationJobListResponse
import team.inreok.getiserver.domain.system.service.OperationJobQueryService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import java.time.LocalDateTime
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "System - Scheduler", description = "개발자용 정기 작업 운영 상태")
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
@RestController
class OperationJobController(
    private val operationJobQueryService: OperationJobQueryService,
) {
    @Operation(
        summary = "정기 작업 운영 상태 조회",
        description = "실제 등록된 6개 정기 작업의 일정, 최근 영속 실행 상태와 수동 Action 지원 여부를 조회한다. 미지원 Action은 실행 API를 제공하지 않는다.",
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        SwaggerApiResponse(responseCode = "400", description = "jobType/status 또는 시각 형식이 올바르지 않음 (TYPE_MISMATCH)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "개발자 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping("/api/v1/admin/system/jobs")
    fun searchJobs(
        @Parameter(description = "정기 작업 업무 식별자(선택)", example = "JOB_COLLECTION")
        @RequestParam(required = false)
        jobType: OperationJobType?,
        @Parameter(description = "작업 상태(선택)", example = "FAILED")
        @RequestParam(required = false)
        status: String?,
        @Parameter(description = "실행 시작 시각 하한(선택)")
        @RequestParam(required = false)
        startAt: LocalDateTime?,
        @Parameter(description = "실행 시작 시각 상한(선택)")
        @RequestParam(required = false)
        endAt: LocalDateTime?,
        @Parameter(description = "Pagination(page: 0부터 시작, size: 기본 20). 정렬 파라미터는 무시된다.")
        pageable: Pageable,
    ): ApiResponse<OperationJobListResponse> =
        ApiResponse.of(operationJobQueryService.search(jobType, status, startAt, endAt, pageable))
}
