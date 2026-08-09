package team.inreok.getiserver.domain.application.exception

import team.inreok.getiserver.global.error.BusinessException

class InvalidFormFieldException(
    reason: String,
) : BusinessException(ApplicationErrorCode.INVALID_FORM_FIELD, reason)
