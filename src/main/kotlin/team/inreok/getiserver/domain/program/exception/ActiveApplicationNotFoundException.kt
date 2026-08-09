package team.inreok.getiserver.domain.program.exception

import team.inreok.getiserver.global.error.BusinessException

class ActiveApplicationNotFoundException : BusinessException(ProgramErrorCode.ACTIVE_APPLICATION_NOT_FOUND)
