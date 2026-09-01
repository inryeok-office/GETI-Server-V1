package team.inreok.getiserver.domain.auth.exception

import team.inreok.getiserver.global.error.BusinessException

/** Web Client가 `clientType=WEB`으로 `authorize`/`callback`을 요청했지만
 * `app.web.oauth.callback-redirect-url`이 비어 있을 때 던진다(Issue #162). 클라이언트 요청 자체는
 * 잘못이 없고 서버 설정 문제이므로 500이다 -- 조용히 기존 JSON 응답으로 대체하지 않는다(운영자가
 * 설정을 놓친 상황을 숨기지 않기 위함). */
class OAuthWebRedirectNotConfiguredException : BusinessException(AuthErrorCode.OAUTH_WEB_REDIRECT_NOT_CONFIGURED)
