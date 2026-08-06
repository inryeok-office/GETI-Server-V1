package team.inreok.getiserver.domain.program.exception

import team.inreok.getiserver.global.error.BusinessException

class ProgramFullException : BusinessException(ProgramErrorCode.PROGRAM_FULL)
