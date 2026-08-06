package team.inreok.getiserver.domain.program.exception

import team.inreok.getiserver.global.error.BusinessException

class ProgramFieldsImmutableException(
    message: String,
) : BusinessException(ProgramErrorCode.PROGRAM_FIELDS_IMMUTABLE, message)
