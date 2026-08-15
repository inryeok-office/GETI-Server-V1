package team.inreok.getiserver.domain.ai.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.ai.entity.type.AiStatus
import java.time.LocalDateTime

@Schema(description = "수동 AI 재분석 접수 응답")
data class AiReanalysisResponse(
    @param:Schema(description = "공고 ID", example = "1")
    val jobId: Long,
    @param:Schema(description = "접수 직후 분석 상태(항상 PENDING)")
    val status: AiStatus,
    @param:Schema(description = "이번 요청이 재분석인지 여부. 최초 분석이 아직 시작되지 않았던 경우에만 false다.", example = "true")
    val isReanalysis: Boolean,
    @param:Schema(description = "지금까지 소비한 수동 재분석 횟수(0~3)", example = "1")
    val reanalysisCount: Int,
    @param:Schema(description = "남은 수동 재분석 가능 횟수(0~3)", example = "2")
    val remainingReanalysisCount: Int,
    @param:Schema(description = "지금 재분석을 다시 요청할 수 있는지", example = "true")
    val canReanalyze: Boolean,
    @param:Schema(description = "이번 요청 접수 시각", example = "2026-08-15T10:00:00")
    val requestedAt: LocalDateTime,
)
