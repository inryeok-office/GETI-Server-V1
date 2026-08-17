package team.inreok.getiserver.domain.recommendation.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "추천 기능 ON/OFF 변경 결과")
data class RecommendationSettingResponse(
    @param:Schema(description = "변경 후 추천 기능 ON/OFF 여부", example = "true")
    val enabled: Boolean,
    @param:Schema(description = "설정이 반영된 시각")
    val updatedAt: LocalDateTime,
)
