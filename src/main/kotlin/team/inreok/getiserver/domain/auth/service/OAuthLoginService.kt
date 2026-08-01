package team.inreok.getiserver.domain.auth.service

// provider는 URL Path Variable(dg|google)을 그대로 받는다. 이번 단계는 google만 지원한다.
interface OAuthLoginService {
    fun getAuthorizationUrl(provider: String): OAuthAuthorization

    // code/state 교환까지만 수행하고 OAuthUserInfo(Provider의 사용자 식별값)를 반환한다.
    // 이 정보로 회원을 조회/생성하고 GETI 자체 Token을 발급하는 것은 Member 도메인 연동이
    // 필요해 이번 단계 범위가 아니다(다음 단계에서 이어서 구현).
    fun exchangeCode(
        provider: String,
        code: String,
        state: String,
    ): OAuthUserInfo
}

data class OAuthAuthorization(
    val authorizationUrl: String,
    val state: String,
)

data class OAuthUserInfo(
    val subject: String,
    val email: String,
)
