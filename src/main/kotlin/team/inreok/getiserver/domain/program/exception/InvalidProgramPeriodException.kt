package team.inreok.getiserver.domain.program.exception

import team.inreok.getiserver.global.error.BusinessException

class InvalidProgramPeriodException(
    message: String,
) : BusinessException(ProgramErrorCode.INVALID_PROGRAM_PERIOD, message)
