package team.inreok.getiserver.domain.auth.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(description = "로그아웃 요청")
data class LogoutRequest(
    @field:NotBlank(message = "refreshToken은 필수입니다.")
    @param:Schema(description = "폐기할 Refresh Token 원문(필수)", example = "3f6e9c2a-1c4b-4b8e-9c7a-1a2b3c4d5e6f")
    val refreshToken: String,
)
