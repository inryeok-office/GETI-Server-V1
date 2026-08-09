package team.inreok.getiserver.domain.program.exception

import team.inreok.getiserver.domain.program.entity.type.ProgramStatus
import team.inreok.getiserver.global.error.BusinessException

class ProgramStatusTransitionNotAllowedException(
    from: ProgramStatus,
    to: ProgramStatus,
) : BusinessException(
        ProgramErrorCode.PROGRAM_STATUS_TRANSITION_NOT_ALLOWED,
        "허용되지 않은 프로그램 상태 변경입니다. ($from -> $to)",
    )
