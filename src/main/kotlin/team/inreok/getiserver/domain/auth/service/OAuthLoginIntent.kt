package team.inreok.getiserver.domain.auth.service

/**
 * OAuth 로그인 요청의 의도다(Issue #229). `authorize`에서 지정하면 `state`에 묶여 Redis에 짧게
 * 보관되고 `/callback`에서 소비된다([OAuthClientType]과 같은 방식).
 *
 * 현재는 재신청([REAPPLY])만 있다. 거절된(REJECTED) 교직원이 이 의도로 다시 OAuth 로그인하면
 * 콜백이 회원을 승인 대기(PENDING)로 되돌린다. 지정하지 않은(=null) 일반 로그인은 기존 동작을
 * 그대로 따른다.
 */
enum class OAuthLoginIntent {
    REAPPLY,
}
