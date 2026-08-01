package team.inreok.getiserver.domain.auth.service

interface TokenService {
    // Refresh Token을 검증하고 즉시 폐기(Rotation)한 뒤 새 Access/Refresh Token 쌍을 발급한다.
    // 새 Access Token은 memberId Claim만 담는다 — Member 도메인 연동 전이라 역할(roles)을 다시
    // 조회할 방법이 없어 이번 단계에서는 빈 목록으로 발급한다(다음 단계에서 Member 연동 후 보완).
    fun refresh(
        refreshToken: String,
        deviceIdentifier: String?,
    ): IssuedTokens

    // memberId는 인증된 호출자(Access Token principal)다. Refresh Token 소유자와 다르면 거부한다
    // (다른 사용자의 세션을 강제 로그아웃시키지 못하도록, 코드 리뷰 Blocker 반영).
    fun logout(
        refreshToken: String,
        memberId: Long,
    )
}

data class IssuedTokens(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresInSeconds: Long,
)
