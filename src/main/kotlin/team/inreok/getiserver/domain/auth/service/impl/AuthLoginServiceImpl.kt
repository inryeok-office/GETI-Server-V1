package team.inreok.getiserver.domain.auth.service.impl

import org.springframework.stereotype.Service
import team.inreok.getiserver.domain.auth.dto.OAuthLoginResponse
import team.inreok.getiserver.domain.auth.service.AuthLoginService
import team.inreok.getiserver.domain.auth.service.OAuthLoginService
import team.inreok.getiserver.domain.auth.service.TokenService
import team.inreok.getiserver.domain.member.query.OAuthMemberPort

/**
 * [AuthLoginService] 구현. Provider 교환·회원 처리·Token 발급을 순서대로 위임만 한다.
 *
 * 각 단계에는 개별 Transaction 경계가 있어(회원 생성, Token 저장) 이 조율 메서드 자체에는
 * `@Transactional`을 두지 않는다 — Provider HTTP 교환은 [OAuthLoginService]가 이미 수행한 뒤이고,
 * 이 메서드를 하나의 긴 Transaction으로 묶을 이유가 없다.
 */
@Service
class AuthLoginServiceImpl(
    private val oAuthLoginService: OAuthLoginService,
    private val oAuthMemberPort: OAuthMemberPort,
    private val tokenService: TokenService,
) : AuthLoginService {
    override fun loginWithOAuth(
        provider: String,
        code: String,
        state: String,
        reapply: Boolean,
    ): OAuthLoginResponse {
        val userInfo = oAuthLoginService.exchangeCode(provider, code, state)
        val member = oAuthMemberPort.findOrCreateByOAuth(provider, userInfo.subject, userInfo.email, reapply)
        // 콜백은 별도 기기 식별자를 받지 않는다. Refresh Token은 deviceIdentifier 없이 발급한다.
        val tokens = tokenService.issueFor(member.memberId, member.roles, deviceIdentifier = null)
        return OAuthLoginResponse(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            accessTokenExpiresInSeconds = tokens.accessTokenExpiresInSeconds,
            memberId = member.memberId,
            roles = member.roles,
            status = member.status,
            isNewMember = member.isNewMember,
        )
    }
}
