package team.inreok.getiserver.domain.auth.service.impl

import org.springframework.stereotype.Service
import team.inreok.getiserver.domain.auth.exception.UnsupportedOAuthProviderException
import team.inreok.getiserver.domain.auth.service.OAuthAuthorization
import team.inreok.getiserver.domain.auth.service.OAuthLoginService
import team.inreok.getiserver.domain.auth.service.OAuthUserInfo

/**
 * URL Path Variable의 provider 값으로 알맞은 [OAuthProviderClient](Google/DG)를 선택해 위임하는
 * Router다. Provider별 실제 흐름(Endpoint, Token 교환 방식)은 각 Client가 담당한다. 등록되지 않은
 * provider는 [UnsupportedOAuthProviderException]으로 거부한다.
 */
@Service
class OAuthLoginServiceImpl(
    clients: List<OAuthProviderClient>,
) : OAuthLoginService {
    private val clientsByProvider: Map<String, OAuthProviderClient> = clients.associateBy { it.provider }

    override fun getAuthorizationUrl(provider: String): OAuthAuthorization = clientFor(provider).createAuthorization()

    override fun exchangeCode(
        provider: String,
        code: String,
        state: String,
    ): OAuthUserInfo = clientFor(provider).exchangeCode(code, state)

    private fun clientFor(provider: String): OAuthProviderClient =
        clientsByProvider[provider.lowercase()] ?: throw UnsupportedOAuthProviderException(provider)
}
