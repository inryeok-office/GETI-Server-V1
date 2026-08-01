package team.inreok.getiserver.domain.auth.exception

import team.inreok.getiserver.global.error.BusinessException

class OAuthLoginFailedException(
    message: String = AuthErrorCode.OAUTH_LOGIN_FAILED.defaultMessage,
) : BusinessException(AuthErrorCode.OAUTH_LOGIN_FAILED, message)
