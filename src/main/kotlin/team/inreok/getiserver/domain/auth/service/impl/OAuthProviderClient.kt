package team.inreok.getiserver.domain.auth.service.impl

import team.inreok.getiserver.domain.auth.service.OAuthAuthorization
import team.inreok.getiserver.domain.auth.service.OAuthUserInfo

/**
 * Provider(Google, DG 등)별 OAuth 흐름을 캡슐화하는 내부 전략이다. Provider마다 Endpoint와 Token
 * 교환 방식(Google: form + PKCE, DG: JSON + client_secret)이 달라, [OAuthLoginServiceImpl]이 provider
 * 문자열로 알맞은 구현을 선택해 위임한다. auth 도메인 내부 구현이므로 Named Interface로 공개하지 않는다.
 */
interface OAuthProviderClient {
    // URL Path Variable과 매칭할 provider 식별자(소문자, 예: "google", "dg").
    val provider: String

    fun createAuthorization(): OAuthAuthorization

    fun exchangeCode(
        code: String,
        state: String,
    ): OAuthUserInfo
}
