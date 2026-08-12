package team.inreok.getiserver.domain.member.exception

import team.inreok.getiserver.global.error.BusinessException

class ProfileViewForbiddenException : BusinessException(MemberErrorCode.PROFILE_VIEW_FORBIDDEN)
