package team.inreok.getiserver.domain.auth.dto

// memberId/roles는 Access Token의 Claim에서 그대로 읽은 값이다. name/email/status 등 Member
// Profile 정보는 auth가 아직 Member 도메인과 연동되지 않아 이번 단계에는 포함하지 않는다.
data class SessionResponse(
    val memberId: Long,
    val roles: List<String>,
)
