package team.inreok.getiserver.domain.ai.exception

import team.inreok.getiserver.global.error.BusinessException

/** 대상 Job이 없거나, 삭제됐거나, 아직 PUBLISHED가 아니어서 AI 분석 대상이 아닐 때 던진다. */
class AiJobNotFoundException(
    jobId: Long,
) : BusinessException(AiErrorCode.JOB_NOT_FOUND, "AI 분석 대상 공고(id=$jobId)를 찾을 수 없습니다.")
