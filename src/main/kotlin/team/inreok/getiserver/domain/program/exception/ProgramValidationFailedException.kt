package team.inreok.getiserver.domain.program.exception

import team.inreok.getiserver.global.error.BusinessException

class ProgramValidationFailedException(
    message: String,
) : BusinessException(ProgramErrorCode.PROGRAM_VALIDATION_FAILED, message)
