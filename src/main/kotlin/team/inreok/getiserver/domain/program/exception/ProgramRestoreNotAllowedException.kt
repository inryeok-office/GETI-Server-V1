package team.inreok.getiserver.domain.program.exception

import team.inreok.getiserver.global.error.BusinessException

// Soft Delete로 deletedAt이 채워지면 findByIdAndDeletedAtIsNull이 먼저 걸러내(PROGRAM_NOT_FOUND)
// 실제로는 도달하지 않지만, 상태 전이표를 한곳에서 읽을 수 있게 남겨둔다(JobServiceImpl의
// JobStatus.DELETED 분기와 동일한 이유).
class ProgramRestoreNotAllowedException : BusinessException(ProgramErrorCode.PROGRAM_RESTORE_NOT_ALLOWED)
