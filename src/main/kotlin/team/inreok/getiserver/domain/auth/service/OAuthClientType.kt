package team.inreok.getiserver.domain.auth.service

/**
 * `authorize` 요청 시점에 Client가 스스로 밝히는 자신의 종류다(OAuth Web Callback 결함 수정,
 * Issue #162). Provider의 `redirect_uri`는 Backend `/callback` 하나로 고정되어 있어(Provider
 * Console을 바꾸지 않는다는 전제), Backend가 이 콜백 응답을 Web 브라우저 복귀용(302 Redirect)으로
 * 만들지, 기존처럼 JSON으로 반환할지는 `/authorize` 시점에 미리 알아야 한다. [OAuthClientTypeStore]가
 * `state`에 이 값을 묶어 Redis에 짧게 보관했다가 `/callback`에서 다시 읽는다.
 *
 * 이 값은 GETI Notion/Figma 등 외부에서 확정된 계약이 아니라 이번 Issue에서 최소한으로 도입한 자체
 * 설계다(PR 본문 DECISION_REQUIRED 참고) -- 지정하지 않으면(`null`) 기존 App/Web 공용 JSON 응답
 * 동작을 그대로 유지해 기존 호출자를 깨지 않는다.
 */
enum class OAuthClientType {
    /** Backend가 `/callback` 처리 후 Frontend Redirect URL로 302 응답한다. */
    WEB,

    /** 지정하지 않은 것과 동일하게 취급한다(기존 JSON 응답 유지). 계약을 명시적으로 드러내고
     * 싶은 App Client를 위해 값 자체는 남겨 둔다. */
    APP,
}
