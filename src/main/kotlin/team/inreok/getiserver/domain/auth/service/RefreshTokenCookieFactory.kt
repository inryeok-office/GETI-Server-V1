package team.inreok.getiserver.domain.auth.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import team.inreok.getiserver.global.security.JwtProperties

/**
 * Refresh Token을 담는 HttpOnly Cookie를 만들고, 요청에서 Refresh Token을 읽는 소스 우선순위를
 * 담당한다(Issue #105).
 *
 * Web Client는 HttpOnly Cookie로 Refresh Token을 주고받아 XSS로 인한 탈취 표면을 줄이고, App
 * Client는 Cookie를 쓰기 어려우므로 [HEADER_NAME] Header로 주고받는다. 기존 Body 방식도 하위 호환을
 * 위해 유지하되, 우선순위는 **Cookie > Header > Body**다.
 *
 * `secure`는 운영에서 true여야 한다(HTTPS 전용). Local(HTTP)에서는 Cookie가 전송되지 않으므로
 * 기본값 false를 쓴다. Cross-Origin 전송에 필요한 CORS `allow-credentials`/SameSite 조정은 이 범위
 * 밖이다(`docs/development/web-api.md` 참고).
 */
@Component
class RefreshTokenCookieFactory(
    private val jwtProperties: JwtProperties,
    @param:Value("\${app.auth.refresh-cookie.secure:false}") private val secure: Boolean,
    @param:Value("\${app.auth.refresh-cookie.same-site:Lax}") private val sameSite: String,
) {
    /** 새 Refresh Token을 담은 Cookie. 만료는 Refresh Token 수명과 같다. */
    fun create(refreshToken: String): ResponseCookie =
        base(refreshToken).maxAge(jwtProperties.refreshTokenExpirationSeconds).build()

    /** 로그아웃 시 Cookie를 즉시 만료시킨다(Max-Age=0). */
    fun expired(): ResponseCookie = base("").maxAge(0).build()

    /**
     * Cookie > [HEADER_NAME] Header > Body 순으로 처음 발견한 non-blank Refresh Token을 고른다.
     * 셋 다 없으면 null.
     */
    fun resolve(
        cookieToken: String?,
        headerToken: String?,
        bodyToken: String?,
    ): String? = sequenceOf(cookieToken, headerToken, bodyToken).firstOrNull { !it.isNullOrBlank() }?.trim()

    private fun base(value: String) =
        ResponseCookie
            .from(COOKIE_NAME, value)
            .httpOnly(true)
            .secure(secure)
            .path(COOKIE_PATH)
            .sameSite(sameSite)

    companion object {
        const val COOKIE_NAME = "refreshToken"
        const val HEADER_NAME = "X-Refresh-Token"
        private const val COOKIE_PATH = "/api/v1/auth"
    }
}
