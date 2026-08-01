package team.inreok.getiserver.domain.auth.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Access/Refresh Token 재발급 응답")
data class TokenRefreshResponse(
    @param:Schema(description = "새로 발급된 Access Token(JWT)", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0...")
    val accessToken: String,
    @param:Schema(
        description = "새로 발급된 Refresh Token(원문, 1회용). 이전 Refresh Token은 즉시 폐기된다.",
        example = "9c7a3f6e-4b8e-1c4b-2a1a-6f5e4d3c2b1a",
    )
    val refreshToken: String,
    @param:Schema(description = "Access Token 만료까지 남은 시간(초)", example = "1800")
    val accessTokenExpiresInSeconds: Long,
)
