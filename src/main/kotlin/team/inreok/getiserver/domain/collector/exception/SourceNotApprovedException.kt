package team.inreok.getiserver.domain.collector.exception

import team.inreok.getiserver.global.error.BusinessException

class SourceNotApprovedException(
    sourceId: Long,
) : BusinessException(CollectorErrorCode.SOURCE_NOT_APPROVED, "수집원(id=$sourceId)이 아직 승인되지 않았습니다.")
