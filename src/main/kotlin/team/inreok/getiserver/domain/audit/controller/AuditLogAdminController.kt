package team.inreok.getiserver.domain.audit.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.audit.dto.AdminAuditLogDetailResponse
import team.inreok.getiserver.domain.audit.dto.AdminAuditLogListResponse
import team.inreok.getiserver.domain.audit.entity.type.AuditResult
import team.inreok.getiserver.domain.audit.service.AuditLogQueryService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import java.time.LocalDateTime
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(
    name = "Audit - 관리자 감사 로그",
    description = "개발자만 시스템에 기록된 감사 로그를 검색하고 상세 조회한다.",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
class AuditLogAdminController(
    private val auditLogQueryService: AuditLogQueryService,
) {
    @Operation(
        summary = "관리자 감사 로그 목록 조회",
        description = "행위자·작업·대상·결과·생성 시각 조건을 AND로 조합해 감사 로그를 최신순으로 페이지 조회한다. 정렬은 createdAt DESC, id DESC로 고정된다.",
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        SwaggerApiResponse(responseCode = "400", description = "쿼리 파라미터 형식 오류 (TYPE_MISMATCH)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "개발자 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping
    fun list(
        @Parameter(description = "행위자 회원 ID 필터(선택)", example = "7")
        @RequestParam(required = false)
        actorId: Long?,
        @Parameter(description = "작업 유형 필터(선택)", example = "COMPANY_UPDATED")
        @RequestParam(required = false)
        actionType: String?,
        @Parameter(description = "대상 유형 필터(선택)", example = "COMPANY")
        @RequestParam(required = false)
        targetType: String?,
        @Parameter(description = "대상 ID 필터(선택)", example = "12")
        @RequestParam(required = false)
        targetId: Long?,
        @Parameter(description = "처리 결과 필터(선택): SUCCESS 또는 FAILURE", example = "SUCCESS")
        @RequestParam(required = false)
        result: AuditResult?,
        @Parameter(description = "생성 시각 시작(포함, ISO-8601, 선택)", example = "2026-08-01T00:00:00")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        startAt: LocalDateTime?,
        @Parameter(description = "생성 시각 종료(포함, ISO-8601, 선택)", example = "2026-08-31T23:59:59")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        endAt: LocalDateTime?,
        @Parameter(description = "Pagination(page: 0부터 시작, size: 기본 20). 정렬 파라미터는 무시된다.")
        pageable: Pageable,
    ): ApiResponse<AdminAuditLogListResponse> =
        ApiResponse.of(
            auditLogQueryService.list(actorId, actionType, targetType, targetId, result, startAt, endAt, pageable),
        )

    @Operation(
        summary = "관리자 감사 로그 상세 조회",
        description = "감사 로그 한 건의 기본 정보와 민감한 원문을 제외한 변경 상세를 조회한다.",
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "개발자 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "404", description = "감사 로그가 없음 (AUDIT_LOG_NOT_FOUND)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping("/{auditLogId}")
    fun get(
        @Parameter(description = "조회할 감사 로그 ID", example = "100") @PathVariable auditLogId: Long,
    ): ApiResponse<AdminAuditLogDetailResponse> = ApiResponse.of(auditLogQueryService.get(auditLogId))
}
