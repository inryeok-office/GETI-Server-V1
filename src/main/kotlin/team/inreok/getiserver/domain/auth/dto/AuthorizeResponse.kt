package team.inreok.getiserver.domain.auth.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "OAuth 로그인 URL 발급 응답")
data class AuthorizeResponse(
    @param:Schema(
        description = "Client가 이동해야 하는 OAuth Provider 인가 URL",
        example = "https://accounts.google.com/o/oauth2/v2/auth?client_id=...&state=b64f2c1a...",
    )
    val authorizationUrl: String,
    @param:Schema(
        description = "CSRF 방지용 state 값(callback 요청 시 그대로 다시 전달)",
        example = "b64f2c1a-9e3d-4b7a-8f21-1a2b3c4d5e6f",
    )
    val state: String,
)
