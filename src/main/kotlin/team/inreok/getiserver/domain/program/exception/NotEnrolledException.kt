package team.inreok.getiserver.domain.program.exception

import team.inreok.getiserver.global.error.BusinessException

class NotEnrolledException : BusinessException(ProgramErrorCode.NOT_ENROLLED)
