package team.inreok.getiserver.domain.auth.exception

import team.inreok.getiserver.global.error.BusinessException

class OAuthStateInvalidException : BusinessException(AuthErrorCode.OAUTH_STATE_INVALID)
