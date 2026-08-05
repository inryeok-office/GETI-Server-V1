package team.inreok.getiserver.domain.application.exception

import team.inreok.getiserver.global.error.BusinessException

class FormNotFoundException(
    formId: Long,
) : BusinessException(ApplicationErrorCode.FORM_NOT_FOUND, "양식(id=$formId)을 찾을 수 없습니다.")
