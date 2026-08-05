package team.inreok.getiserver.domain.application.exception

import team.inreok.getiserver.domain.application.dto.FormAction
import team.inreok.getiserver.domain.application.entity.type.FormStatus
import team.inreok.getiserver.global.error.BusinessException

class FormActionInvalidException(
    action: FormAction,
    currentStatus: FormStatus,
) : BusinessException(
        ApplicationErrorCode.FORM_ACTION_INVALID,
        "현재 상태($currentStatus)에서는 $action Action을 수행할 수 없습니다.",
    )
