package team.inreok.getiserver.domain.auth.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * OAuth 로그인 콜백 응답이다. code/state 교환으로 회원을 조회·생성하고 GETI 자체 Access/Refresh
 * Token을 발급한 결과를 담는다(Issue #48). Frontend는 [isNewMember]/[status]로 진입 화면
 * (신규 → 프로필 등록, 승인 대기 → 안내)을 분기한다.
 */
@Schema(description = "OAuth 로그인 콜백 응답. GETI 자체 Access/Refresh Token과 회원 상태를 반환한다.")
data class OAuthLoginResponse(
    @param:Schema(description = "GETI 자체 Access Token(JWT)", example = "eyJhbGciOiJIUzI1NiJ9...")
    val accessToken: String,
    @param:Schema(description = "GETI 자체 Refresh Token(원문, 재발급·로그아웃에 사용)", example = "Zm9vYmFyYmF6...")
    val refreshToken: String,
    @param:Schema(description = "Access Token 만료까지 남은 시간(초)", example = "1800")
    val accessTokenExpiresInSeconds: Long,
    @param:Schema(description = "로그인된 회원 ID", example = "1")
    val memberId: Long,
    @param:Schema(description = "회원 Role 목록", example = "[\"TEACHER\"]")
    val roles: List<String>,
    @param:Schema(description = "회원 상태(PENDING/ACTIVE 등). 최초 로그인은 PENDING(승인 대기)", example = "PENDING")
    val status: String,
    @param:Schema(description = "이번 로그인에서 새로 생성된 회원이면 true", example = "true")
    val isNewMember: Boolean,
)
