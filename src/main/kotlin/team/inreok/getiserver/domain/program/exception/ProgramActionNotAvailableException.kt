package team.inreok.getiserver.domain.program.exception

import team.inreok.getiserver.global.error.BusinessException

class ProgramActionNotAvailableException(
    message: String,
) : BusinessException(ProgramErrorCode.PROGRAM_ACTION_NOT_AVAILABLE, message)
