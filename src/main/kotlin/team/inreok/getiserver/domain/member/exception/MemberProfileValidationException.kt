package team.inreok.getiserver.domain.member.exception

import team.inreok.getiserver.global.error.BusinessException

class MemberProfileValidationException(
    message: String,
) : BusinessException(MemberErrorCode.PROFILE_VALIDATION_FAILED, message)
