package team.inreok.getiserver.domain.auth.dto

data class TokenRefreshResponse(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresInSeconds: Long,
)
