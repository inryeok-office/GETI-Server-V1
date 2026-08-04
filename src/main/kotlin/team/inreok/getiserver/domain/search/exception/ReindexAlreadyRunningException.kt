package team.inreok.getiserver.domain.search.exception

import team.inreok.getiserver.global.error.BusinessException

class ReindexAlreadyRunningException :
    BusinessException(
        SearchErrorCode.REINDEX_ALREADY_RUNNING,
        "이미 전체 재색인이 진행 중입니다.",
    )
