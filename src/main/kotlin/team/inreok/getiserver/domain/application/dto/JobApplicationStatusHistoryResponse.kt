package team.inreok.getiserver.domain.application.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus
import java.time.LocalDateTime

@Schema(description = "지원서 상태 변경 이력 1건")
data class JobApplicationStatusHistoryResponse(
    @param:Schema(description = "이력 ID", example = "1")
    val historyId: Long,
    @param:Schema(description = "변경 전 상태", example = "SUBMITTED")
    val fromStatus: JobApplicationStatus,
    @param:Schema(description = "변경 후 상태", example = "REVISION_REQUESTED")
    val toStatus: JobApplicationStatus,
    @param:Schema(
        description = "실행된 Action(학생 JobApplicationAction 또는 교사 JobApplicationAdminAction)",
        example = "REQUEST_REVISION",
    )
    val action: String,
    @param:Schema(description = "Action 수행자 Member ID", example = "3")
    val actorMemberId: Long,
    @param:Schema(description = "변경 사유(선택)", nullable = true, example = "포트폴리오 링크를 추가해주세요.")
    val reason: String?,
    @param:Schema(description = "변경 시각")
    val createdAt: LocalDateTime,
)
