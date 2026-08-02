package team.inreok.getiserver.domain.job.exception

import team.inreok.getiserver.global.error.BusinessException

/**
 * Bean Validation(`@field:Size` 등)으로 표현할 수 없는 공고 고유 규칙을 위반했을 때 사용한다.
 * Message는 우리가 직접 작성한 안전한 문구만 담고 입력값 원문을 넣지 않는다.
 */
class JobValidationFailedException(
    reason: String,
) : BusinessException(JobErrorCode.JOB_VALIDATION_FAILED, reason)
