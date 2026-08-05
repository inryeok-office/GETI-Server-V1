package team.inreok.getiserver.domain.application.exception

import team.inreok.getiserver.global.error.BusinessException

class ApplicationNotFoundException(
    applicationId: Long,
) : BusinessException(ApplicationErrorCode.APPLICATION_NOT_FOUND, "지원서(id=$applicationId)를 찾을 수 없습니다.")
