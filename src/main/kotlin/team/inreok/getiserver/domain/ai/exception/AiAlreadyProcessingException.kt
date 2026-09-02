package team.inreok.getiserver.domain.ai.exception

import team.inreok.getiserver.global.error.BusinessException

class AiAlreadyProcessingException(
    jobId: Long,
) : BusinessException(AiErrorCode.AI_ALREADY_PROCESSING, "공고(id=$jobId)의 AI 분석이 이미 진행 중입니다.")
