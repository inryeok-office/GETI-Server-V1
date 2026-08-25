package team.inreok.getiserver.domain.auth.service

interface TokenService {
    // 인증에 성공한 회원에게 새 Access/Refresh Token 쌍을 발급한다(OAuth 로그인 직후 사용, Issue
    // #48). Access Token은 전달받은 roles를 그대로 Claim에 담는다.
    fun issueFor(
        memberId: Long,
        roles: List<String>,
        deviceIdentifier: String?,
    ): IssuedTokens

    // Refresh Token을 검증하고 즉시 폐기(Rotation)한 뒤 새 Access/Refresh Token 쌍을 발급한다.
    // 새 Access Token의 roles Claim은 Member 도메인에서 현재 역할을 다시 조회해 채운다.
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
