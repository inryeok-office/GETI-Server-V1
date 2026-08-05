package team.inreok.getiserver.domain.application.exception

import team.inreok.getiserver.global.error.BusinessException

class ApplicationActionNotAvailableException(
    message: String = ApplicationErrorCode.APPLICATION_ACTION_NOT_AVAILABLE.defaultMessage,
) : BusinessException(ApplicationErrorCode.APPLICATION_ACTION_NOT_AVAILABLE, message)
