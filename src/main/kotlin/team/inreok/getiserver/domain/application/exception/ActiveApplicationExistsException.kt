package team.inreok.getiserver.domain.application.exception

import team.inreok.getiserver.global.error.BusinessException

class ActiveApplicationExistsException : BusinessException(ApplicationErrorCode.ACTIVE_APPLICATION_EXISTS)
