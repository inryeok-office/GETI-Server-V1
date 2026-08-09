package team.inreok.getiserver.domain.program.exception

import team.inreok.getiserver.global.error.BusinessException

class InvalidTargetGradeException(
    message: String,
) : BusinessException(ProgramErrorCode.INVALID_TARGET_GRADE, message)
