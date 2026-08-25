package team.inreok.getiserver.domain.auth.dto

import io.swagger.v3.oas.annotations.media.Schema

// refreshToken은 Cookie 또는 X-Refresh-Token Header로도 전달할 수 있어 선택 필드다(Issue #105).
// 서버는 Cookie > Header > Body 순으로 읽으며, 셋 다 없으면 400(REFRESH_TOKEN_REQUIRED)이다.
@Schema(description = "Access/Refresh Token 재발급 요청. refreshToken은 Cookie/X-Refresh-Token Header/Body 중 하나로 전달한다.")
data class TokenRefreshRequest(
    @param:Schema(
        description = "재발급에 사용할 Refresh Token 원문(선택, Cookie/Header로 전달 시 생략 가능, 1회용)",
        example = "3f6e9c2a-1c4b-4b8e-9c7a-1a2b3c4d5e6f",
        nullable = true,
    )
    val refreshToken: String? = null,
    @param:Schema(description = "새 Refresh Token에 함께 기록할 기기 식별값(선택)", example = "web-chrome-macos", nullable = true)
    val deviceIdentifier: String? = null,
)
