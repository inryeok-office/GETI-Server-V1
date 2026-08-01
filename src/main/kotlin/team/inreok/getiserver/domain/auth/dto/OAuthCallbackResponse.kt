package team.inreok.getiserver.domain.auth.dto

// Member 도메인 연동(회원 조회/생성) 전 단계이므로, OAuth Provider가 반환한 사용자 식별값만
// 그대로 응답한다. 회원 조회/생성과 GETI 자체 Token 발급은 후속 PR에서 이어서 구현한다.
data class OAuthCallbackResponse(
    val subject: String,
    val email: String,
)
