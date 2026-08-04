package team.inreok.getiserver.domain.collector.exception

import team.inreok.getiserver.global.error.BusinessException

class JobSourceNotFoundException(
    sourceId: Long,
) : BusinessException(CollectorErrorCode.JOB_SOURCE_NOT_FOUND, "수집원(id=$sourceId)을 찾을 수 없습니다.")
