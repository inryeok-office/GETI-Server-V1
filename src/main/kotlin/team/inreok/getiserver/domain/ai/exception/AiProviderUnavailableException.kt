package team.inreok.getiserver.domain.ai.exception

import team.inreok.getiserver.global.error.BusinessException

/**
 * OpenAI Provider가 설정되지 않은 상태(`OPENAI_API_KEY` 미설정 등)에서 수동 재분석을 요청했을
 * 때 던진다. Provider가 준비되지 않았음이 이미 확실하므로, 실패가 예정된 분석을 접수해 사용자의
 * 수동 재분석 횟수를 소비하지 않고 요청 자체를 즉시 거부한다(Code Review 지적 사항, Issue #132).
 */
class AiProviderUnavailableException(
    jobId: Long,
) : BusinessException(AiErrorCode.AI_PROVIDER_UNAVAILABLE, "공고(id=$jobId)의 AI 분석을 지금 사용할 수 없습니다.")
