package team.inreok.getiserver.domain.inquiry.exception

import team.inreok.getiserver.global.error.BusinessException

class InquiryNotFoundException(
    inquiryId: Long,
) : BusinessException(InquiryErrorCode.INQUIRY_NOT_FOUND, "요청한 문의를 찾을 수 없습니다. (inquiryId=$inquiryId)")
