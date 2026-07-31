package team.inreok.getiserver.domain.member.exception

import team.inreok.getiserver.global.error.BusinessException

class MemberProfileNotFoundException(
    memberId: Long,
) : BusinessException(MemberErrorCode.PROFILE_NOT_FOUND, "회원(id=$memberId)의 프로필을 찾을 수 없습니다.")
