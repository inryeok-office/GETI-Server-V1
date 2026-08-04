package team.inreok.getiserver.domain.collector.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import team.inreok.getiserver.domain.collector.entity.type.CollectorAction

@Schema(description = "수동 수집·동기화 실행 요청")
data class CollectorActionRequest(
    @param:Schema(description = "실행 종류")
    @field:NotNull
    val action: CollectorAction?,
    @param:Schema(description = "대상 수집원 ID 목록")
    @field:NotEmpty
    val sourceIds: List<Long>?,
)
