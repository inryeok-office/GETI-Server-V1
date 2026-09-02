package team.inreok.getiserver.domain.auth.exception

import team.inreok.getiserver.global.error.BusinessException

/**
 * Refresh Token이 Cookie/`X-Refresh-Token` Header/Body 어디에도 없어 재발급·로그아웃을 수행할 수
 * 없는 경우다(Issue #105).
 */
class RefreshTokenRequiredException : BusinessException(AuthErrorCode.REFRESH_TOKEN_REQUIRED)
