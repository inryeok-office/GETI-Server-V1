package team.inreok.getiserver.domain.ai.exception

import team.inreok.getiserver.global.error.BusinessException

class AiReanalysisLimitExceededException(
    jobId: Long,
    maxCount: Int,
) : BusinessException(
        AiErrorCode.AI_REANALYSIS_LIMIT,
        "공고(id=$jobId)는 수동 재분석 가능 횟수(최대 ${maxCount}회)를 모두 사용했습니다.",
    )
