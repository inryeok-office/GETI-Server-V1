package team.inreok.getiserver.domain.auth.exception

import team.inreok.getiserver.global.error.BusinessException

class InvalidRefreshTokenException : BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN)
