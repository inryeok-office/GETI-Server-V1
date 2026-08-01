package team.inreok.getiserver.domain.auth.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(description = "Access/Refresh Token 재발급 요청")
data class TokenRefreshRequest(
    @field:NotBlank(message = "refreshToken은 필수입니다.")
    @param:Schema(description = "재발급에 사용할 Refresh Token 원문(필수, 1회용)", example = "3f6e9c2a-1c4b-4b8e-9c7a-1a2b3c4d5e6f")
    val refreshToken: String,
    @param:Schema(description = "새 Refresh Token에 함께 기록할 기기 식별값(선택)", example = "web-chrome-macos", nullable = true)
    val deviceIdentifier: String? = null,
)
