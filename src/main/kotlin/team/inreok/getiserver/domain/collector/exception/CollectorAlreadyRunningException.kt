package team.inreok.getiserver.domain.collector.exception

import team.inreok.getiserver.global.error.BusinessException

class CollectorAlreadyRunningException(
    sourceId: Long,
) : BusinessException(
        CollectorErrorCode.COLLECTOR_ALREADY_RUNNING,
        "수집원(id=$sourceId)의 실행이 이미 진행 중입니다.",
    )
