package team.inreok.getiserver.domain.collector.exception

import team.inreok.getiserver.global.error.BusinessException

class SourceNotConfiguredException(
    sourceId: Long,
) : BusinessException(CollectorErrorCode.SOURCE_NOT_CONFIGURED, "수집원(id=$sourceId)이 아직 설정되지 않았습니다.")
