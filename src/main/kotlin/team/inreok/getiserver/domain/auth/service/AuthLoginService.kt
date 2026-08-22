package team.inreok.getiserver.domain.auth.service

import team.inreok.getiserver.domain.auth.dto.OAuthLoginResponse

/**
 * OAuth 로그인 전 과정을 조율한다(Issue #48): Provider code/state 교환 → 회원 조회·생성 →
 * GETI 자체 Token 발급. Provider 통신은 [OAuthLoginService], 회원 처리는 Member 도메인 공개
 * 계약([team.inreok.getiserver.domain.member.query.OAuthMemberPort]), Token 발급은
 * [TokenService]가 담당하고, 이 Service는 그 조합만 책임진다.
 */
interface AuthLoginService {
    /**
     * @param reapply 거절된 교직원의 재신청(intent=REAPPLY) 요청이면 true다(Issue #229). true이고
     *   기존 회원이 REJECTED면 승인 대기(PENDING)로 되돌린 뒤 로그인을 완료하고, false이고 REJECTED면
     *   거절 사유를 담아 거부한다.
     */
    fun loginWithOAuth(
        provider: String,
        code: String,
        state: String,
        reapply: Boolean = false,
    ): OAuthLoginResponse
}
