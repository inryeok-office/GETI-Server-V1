package team.inreok.getiserver.domain.auth.service

interface TokenService {
    // Refresh Token을 검증하고 즉시 폐기(Rotation)한 뒤 새 Access/Refresh Token 쌍을 발급한다.
    // 새 Access Token은 memberId Claim만 담는다 — Member 도메인 연동 전이라 역할(roles)을 다시
    // 조회할 방법이 없어 이번 단계에서는 빈 목록으로 발급한다(다음 단계에서 Member 연동 후 보완).
    fun refresh(
        refreshToken: String,
        deviceIdentifier: String?,
    ): IssuedTokens

    fun logout(refreshToken: String)
}

data class IssuedTokens(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresInSeconds: Long,
)
