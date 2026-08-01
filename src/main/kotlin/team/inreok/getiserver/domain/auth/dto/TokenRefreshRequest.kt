package team.inreok.getiserver.domain.auth.dto

import jakarta.validation.constraints.NotBlank

data class TokenRefreshRequest(
    @field:NotBlank(message = "refreshToken은 필수입니다.")
    val refreshToken: String,
    val deviceIdentifier: String? = null,
)
