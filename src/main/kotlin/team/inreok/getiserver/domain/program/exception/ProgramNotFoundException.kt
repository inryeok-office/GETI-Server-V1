package team.inreok.getiserver.domain.program.exception

import team.inreok.getiserver.global.error.BusinessException

class ProgramNotFoundException(
    programId: Long,
) : BusinessException(ProgramErrorCode.PROGRAM_NOT_FOUND, "요청한 프로그램을 찾을 수 없습니다. (programId=$programId)")
