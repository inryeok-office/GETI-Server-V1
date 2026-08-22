package team.inreok.getiserver.domain.audit.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.audit.entity.type.AuditResult
import java.time.LocalDateTime

@Schema(description = "관리자 감사 로그 목록 결과. page는 0부터 시작한다.")
data class AdminAuditLogListResponse(
    @param:Schema(description = "감사 로그 목록")
    val content: List<AdminAuditLogListItemResponse>,
    @param:Schema(description = "현재 페이지 번호", example = "0")
    val page: Int,
    @param:Schema(description = "페이지 크기", example = "20")
    val size: Int,
    @param:Schema(description = "전체 결과 수", example = "42")
    val totalElements: Long,
    @param:Schema(description = "전체 페이지 수", example = "3")
    val totalPages: Int,
    @param:Schema(description = "첫 페이지 여부", example = "true")
    val first: Boolean,
    @param:Schema(description = "마지막 페이지 여부", example = "false")
    val last: Boolean,
)

@Schema(description = "관리자 감사 로그 목록 항목")
data class AdminAuditLogListItemResponse(
    @param:Schema(description = "감사 로그 ID", example = "100")
    val auditLogId: Long,
    @param:Schema(description = "행위자 회원 ID. 탈퇴 등으로 회원이 없으면 null", example = "7", nullable = true)
    val actorId: Long?,
    @param:Schema(description = "행위자 이름. 회원이 없으면 null", example = "홍길동", nullable = true)
    val actorName: String?,
    @param:Schema(description = "수행 작업", example = "COMPANY_UPDATED")
    val actionType: String,
    @param:Schema(description = "대상 종류", example = "COMPANY")
    val targetType: String,
    @param:Schema(description = "대상 ID", example = "12", nullable = true)
    val targetId: Long?,
    @param:Schema(description = "처리 결과. 과거 로그는 null일 수 있음", example = "SUCCESS", nullable = true)
    val result: AuditResult?,
    @param:Schema(description = "요청 경로. 기록되지 않은 과거 로그는 null", example = "/api/v1/admin/companies/12", nullable = true)
    val requestPath: String?,
    @param:Schema(description = "민감한 값이 제거된 결과 메시지", example = "기업 정보가 수정되었습니다.", nullable = true)
    val maskedDetail: String?,
    @param:Schema(description = "로그 생성 시각", example = "2026-08-22T10:15:30")
    val createdAt: LocalDateTime,
)

@Schema(description = "관리자 감사 로그 상세")
data class AdminAuditLogDetailResponse(
    @param:Schema(description = "감사 로그 ID", example = "100")
    val auditLogId: Long,
    @param:Schema(description = "행위자 회원 ID. 탈퇴 등으로 회원이 없으면 null", example = "7", nullable = true)
    val actorId: Long?,
    @param:Schema(description = "행위자 이름. 회원이 없으면 null", example = "홍길동", nullable = true)
    val actorName: String?,
    @param:Schema(description = "수행 작업", example = "COMPANY_UPDATED")
    val actionType: String,
    @param:Schema(description = "대상 종류", example = "COMPANY")
    val targetType: String,
    @param:Schema(description = "대상 ID", example = "12", nullable = true)
    val targetId: Long?,
    @param:Schema(description = "처리 결과. 과거 로그는 null일 수 있음", example = "SUCCESS", nullable = true)
    val result: AuditResult?,
    @param:Schema(description = "요청 경로. 기록되지 않은 과거 로그는 null", example = "/api/v1/admin/companies/12", nullable = true)
    val requestPath: String?,
    @param:Schema(description = "민감한 값이 제거된 결과 메시지", example = "기업 정보가 수정되었습니다.", nullable = true)
    val maskedDetail: String?,
    @param:Schema(description = "로그 생성 시각", example = "2026-08-22T10:15:30")
    val createdAt: LocalDateTime,
    @param:Schema(description = "민감한 원문을 포함하지 않는 변경 목록")
    val changes: List<AdminAuditLogChangeResponse>,
)

@Schema(description = "감사 로그 변경 한 건")
data class AdminAuditLogChangeResponse(
    @param:Schema(description = "변경된 필드명", example = "name")
    val field: String,
    @param:Schema(description = "변경 전 값. 민감한 값은 null", example = "이전 기업명", nullable = true)
    val before: String?,
    @param:Schema(description = "변경 후 값. 민감한 값은 null", example = "새 기업명", nullable = true)
    val after: String?,
)
