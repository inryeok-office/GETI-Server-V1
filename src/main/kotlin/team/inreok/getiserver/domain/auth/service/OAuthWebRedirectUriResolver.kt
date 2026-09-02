package team.inreok.getiserver.domain.auth.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import team.inreok.getiserver.domain.auth.exception.OAuthWebRedirectNotConfiguredException
import java.net.URI

/**
 * Web Client의 OAuth 완료 Redirect 대상 URI를 만든다(OAuth Web Callback 결함 수정, Issue #162).
 * Redirect 대상은 항상 서버 Configuration(`app.web.oauth.callback-redirect-url`)만으로 결정하고,
 * 요청 Parameter로 임의 URL을 받지 않는다(Open Redirect 방지). 이 값의 실제 운영 URL은 이번
 * 작업에서 알 수 없어 임의로 채우지 않았다(DECISION_REQUIRED, PR 본문 참고) -- 값이 비어 있는데
 * Web Redirect가 요청되면 [OAuthWebRedirectNotConfiguredException]으로 명확히 실패한다.
 *
 * 성공/실패 모두 같은 Frontend Callback Page로 보낸다 -- 실패는 `error` Query Parameter 하나만
 * 덧붙인다(Provider 원본 오류, Stack Trace, code, state는 절대 담지 않는다).
 */
@Component
class OAuthWebRedirectUriResolver(
    @param:Value("\${app.web.oauth.callback-redirect-url:}") private val callbackRedirectUrl: String,
) {
    /** [callbackRedirectUrl]이 비어 있으면 던진다. `authorize` 단계에서 미리 호출해 Provider
     * 로그인 왕복을 시작하기 전에 설정 누락을 빠르게 걸러낸다(Fail-Fast). */
    fun requireConfigured() {
        if (callbackRedirectUrl.isBlank()) throw OAuthWebRedirectNotConfiguredException()
    }

    /**
     * 성공 Redirect URI를 만든다. Web Callback은 Body가 없으므로 신규 회원 여부를 URL의
     * `newMember` Query Parameter로만 전달한다(Issue #162 후속). Access/Refresh Token은 절대
     * URL에 담지 않는다 -- Refresh Token은 [OAuthController]가 HttpOnly Cookie로만 내려준다.
     */
    fun successUri(newMember: Boolean): URI {
        requireConfigured()
        return UriComponentsBuilder
            .fromUriString(callbackRedirectUrl)
            .queryParam("newMember", newMember)
            .build(true)
            .toUri()
    }

    fun failureUri(errorCode: String): URI {
        requireConfigured()
        return UriComponentsBuilder
            .fromUriString(callbackRedirectUrl)
            .queryParam("error", errorCode)
            .build(true)
            .toUri()
    }
}
