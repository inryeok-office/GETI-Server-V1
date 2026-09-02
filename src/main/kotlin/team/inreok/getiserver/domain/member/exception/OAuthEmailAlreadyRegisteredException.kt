package team.inreok.getiserver.domain.member.exception

import team.inreok.getiserver.global.error.BusinessException

/**
 * OAuth 최초 로그인으로 회원을 생성하려는데 같은 이메일이 이미 다른 OAuth 계정
 * (`oauth_provider`+`oauth_subject`)으로 가입되어 있는 경우다(`uk_members_email` 충돌, Issue #48).
 * 이메일 원문은 운영자가 식별할 안전한 값이므로 Message에 포함한다.
 */
class OAuthEmailAlreadyRegisteredException(
    email: String,
) : BusinessException(
        MemberErrorCode.OAUTH_EMAIL_ALREADY_REGISTERED,
        "이미 다른 방식으로 가입된 이메일입니다. (email=$email)",
    )
