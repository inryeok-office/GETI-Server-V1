package team.inreok.getiserver.domain.program.exception

import team.inreok.getiserver.global.error.BusinessException

class ProgramClosedException : BusinessException(ProgramErrorCode.PROGRAM_CLOSED)
