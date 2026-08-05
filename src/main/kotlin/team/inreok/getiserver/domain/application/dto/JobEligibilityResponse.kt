package team.inreok.getiserver.domain.application.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.application.entity.type.JobApplicationEligibilityReason

@Schema(description = "학생 지원 가능 여부. 서버가 계산한 값이며 클라이언트는 직접 계산하지 않는다(요구사항 7절).")
data class JobEligibilityResponse(
    @param:Schema(description = "지원 가능 여부", example = "true")
    val canApply: Boolean,
    @param:Schema(description = "지원 불가 사유(가능하면 AVAILABLE)", example = "AVAILABLE")
    val eligibilityReason: JobApplicationEligibilityReason,
    @param:Schema(description = "사유에 대응하는 안내 문구", example = "지원 가능한 공고입니다.")
    val eligibilityMessage: String,
    @param:Schema(description = "현재 화면에서 시도할 수 있는 Action 목록. 지원 가능하면 [\"CREATE_DRAFT\"], 아니면 빈 배열.")
    val availableActions: List<String>,
)
