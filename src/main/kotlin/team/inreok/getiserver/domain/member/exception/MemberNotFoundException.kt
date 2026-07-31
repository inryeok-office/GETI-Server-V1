package team.inreok.getiserver.domain.member.exception

import team.inreok.getiserver.global.error.BusinessException

class MemberNotFoundException(
    memberId: Long,
) : BusinessException(MemberErrorCode.MEMBER_NOT_FOUND, "회원(id=$memberId)을 찾을 수 없습니다.")
