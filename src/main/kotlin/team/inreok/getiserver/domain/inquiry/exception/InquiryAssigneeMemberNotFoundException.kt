package team.inreok.getiserver.domain.inquiry.exception

import team.inreok.getiserver.global.error.BusinessException

class InquiryAssigneeMemberNotFoundException(
    memberId: Long,
) : BusinessException(InquiryErrorCode.MEMBER_NOT_FOUND, "요청한 회원을 찾을 수 없습니다. (memberId=$memberId)")
