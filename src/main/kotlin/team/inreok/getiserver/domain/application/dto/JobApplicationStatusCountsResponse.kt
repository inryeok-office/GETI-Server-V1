package team.inreok.getiserver.domain.application.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus

@Schema(description = "관리자 지원서 상태별 건수 결과. DRAFT 상태는 관리자 목록과 동일하게 제외된다.")
data class JobApplicationStatusCountsResponse(
    @param:Schema(description = "관리자 목록 대상 지원서의 전체 건수", example = "42")
    val totalCount: Long,
    @param:Schema(
        description = "상태별 지원서 건수. 현재 상태 Enum의 DRAFT를 제외한 모든 상태가 0건이어도 포함된다.",
    )
    val counts: Map<JobApplicationStatus, Long>,
)
