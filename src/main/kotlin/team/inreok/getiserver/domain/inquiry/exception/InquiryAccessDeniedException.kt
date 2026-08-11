package team.inreok.getiserver.domain.inquiry.exception

import team.inreok.getiserver.global.error.BusinessException

class InquiryAccessDeniedException : BusinessException(InquiryErrorCode.INQUIRY_ACCESS_DENIED)
