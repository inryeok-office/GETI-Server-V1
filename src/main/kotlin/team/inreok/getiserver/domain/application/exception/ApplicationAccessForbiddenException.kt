package team.inreok.getiserver.domain.application.exception

import team.inreok.getiserver.global.error.BusinessException

class ApplicationAccessForbiddenException : BusinessException(ApplicationErrorCode.APPLICATION_ACCESS_FORBIDDEN)
