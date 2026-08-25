package team.inreok.getiserver.domain.application.exception

import team.inreok.getiserver.global.error.BusinessException

class ApplicationRequiredAnswerMissingException(
    missingFieldIds: List<String>,
) : BusinessException(
        ApplicationErrorCode.APPLICATION_REQUIRED_ANSWER_MISSING,
        "필수 항목에 대한 답변이 누락되었습니다: ${missingFieldIds.joinToString(", ")}",
    )
