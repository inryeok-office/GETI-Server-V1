package team.inreok.getiserver.domain.inquiry.exception

import team.inreok.getiserver.global.error.BusinessException

class InquiryAlreadyClosedException(
    inquiryId: Long,
) : BusinessException(
        InquiryErrorCode.INQUIRY_ALREADY_CLOSED,
        "이미 종료된 문의에는 답변을 등록할 수 없습니다. (inquiryId=$inquiryId)",
    )
