package team.inreok.getiserver.domain.auth.dto

import jakarta.validation.constraints.NotBlank

data class LogoutRequest(
    @field:NotBlank(message = "refreshToken은 필수입니다.")
    val refreshToken: String,
)
