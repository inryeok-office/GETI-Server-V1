package team.inreok.getiserver.domain.auth.service

import team.inreok.getiserver.domain.auth.dto.OAuthLoginResponse

/**
 * OAuth 로그인 전 과정을 조율한다(Issue #48): Provider code/state 교환 → 회원 조회·생성 →
 * GETI 자체 Token 발급. Provider 통신은 [OAuthLoginService], 회원 처리는 Member 도메인 공개
 * 계약([team.inreok.getiserver.domain.member.query.OAuthMemberPort]), Token 발급은
 * [TokenService]가 담당하고, 이 Service는 그 조합만 책임진다.
 */
interface AuthLoginService {
    fun loginWithOAuth(
        provider: String,
        code: String,
        state: String,
    ): OAuthLoginResponse
}
