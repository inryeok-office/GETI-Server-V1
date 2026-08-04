package team.inreok.getiserver.domain.collector.exception

import team.inreok.getiserver.global.error.BusinessException

class CollectionRunNotFoundException(
    runId: Long,
) : BusinessException(CollectorErrorCode.COLLECTION_RUN_NOT_FOUND, "수집 실행(id=$runId)을 찾을 수 없습니다.")
