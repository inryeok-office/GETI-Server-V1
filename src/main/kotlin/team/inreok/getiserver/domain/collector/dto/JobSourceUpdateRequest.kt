package team.inreok.getiserver.domain.collector.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(description = "수집원 활성 상태 변경 요청")
data class JobSourceUpdateRequest(
    @param:Schema(description = "변경할 활성화 여부", example = "true")
    @field:NotNull
    val enabled: Boolean?,
)
