package team.inreok.getiserver.domain.recommendation.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "추천 기능 ON/OFF 변경 요청")
data class RecommendationSettingRequest(
    @param:Schema(description = "추천 기능을 켤지(true) 끌지(false) 여부", example = "true")
    val enabled: Boolean,
)
