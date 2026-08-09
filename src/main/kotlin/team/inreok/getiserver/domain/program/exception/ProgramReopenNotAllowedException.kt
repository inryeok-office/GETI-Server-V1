package team.inreok.getiserver.domain.program.exception

import team.inreok.getiserver.global.error.BusinessException

class ProgramReopenNotAllowedException : BusinessException(ProgramErrorCode.PROGRAM_REOPEN_NOT_ALLOWED)
