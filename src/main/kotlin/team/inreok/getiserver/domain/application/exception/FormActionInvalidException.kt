package team.inreok.getiserver.domain.application.exception

import team.inreok.getiserver.global.error.BusinessException

/**
 * 허용되지 않는 양식 상태 전이다. `POST .../actions`(ACTIVATE/ARCHIVE)뿐 아니라
 * `PATCH .../{formId}`의 `status` Field 변경도 같은 상태 머신 규칙을 따라야 하므로
 * (코드 리뷰 Finding #1, PR #77), 두 경로가 공유하는 하나의 예외로 둔다.
 */
class FormActionInvalidException(
    message: String,
) : BusinessException(ApplicationErrorCode.FORM_ACTION_INVALID, message)
