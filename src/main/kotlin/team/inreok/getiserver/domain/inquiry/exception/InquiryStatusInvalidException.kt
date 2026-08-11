package team.inreok.getiserver.domain.inquiry.exception

import team.inreok.getiserver.global.error.BusinessException

class InquiryStatusInvalidException(
    message: String,
) : BusinessException(InquiryErrorCode.INQUIRY_STATUS_INVALID, message)
