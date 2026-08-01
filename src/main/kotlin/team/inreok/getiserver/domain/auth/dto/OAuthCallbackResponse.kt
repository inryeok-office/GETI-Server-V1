package team.inreok.getiserver.domain.auth.dto

import io.swagger.v3.oas.annotations.media.Schema

// Member 도메인 연동(회원 조회/생성) 전 단계이므로, OAuth Provider가 반환한 사용자 식별값만
// 그대로 응답한다. 회원 조회/생성과 GETI 자체 Token 발급은 후속 PR에서 이어서 구현한다.
@Schema(description = "OAuth 콜백 처리 응답. GETI 자체 Access/Refresh Token은 아직 발급하지 않는다(후속 작업).")
data class OAuthCallbackResponse(
    @param:Schema(description = "Provider가 발급한 사용자 식별값(Google/DG의 subject)", example = "108234567890123456789")
    val subject: String,
    @param:Schema(description = "Provider UserInfo에서 조회한 이메일", example = "student@example.com")
    val email: String,
)
