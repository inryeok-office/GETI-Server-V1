package team.inreok.getiserver.domain.auth.dto

import io.swagger.v3.oas.annotations.media.Schema

// memberId/roles는 Access Token의 Claim에서 그대로 읽은 값이다. name/email/status 등 Member
// Profile 정보는 auth가 아직 Member 도메인과 연동되지 않아 이번 단계에는 포함하지 않는다.
@Schema(description = "현재 로그인 사용자 확인 응답. Access Token Claim에서 그대로 읽은 값만 담는다.")
data class SessionResponse(
    @param:Schema(description = "Access Token Subject에서 읽은 회원 ID", example = "1")
    val memberId: Long,
    @param:Schema(description = "Access Token roles Claim(ROLE_ 접두어 제거)", example = "[\"STUDENT\"]")
    val roles: List<String>,
)
