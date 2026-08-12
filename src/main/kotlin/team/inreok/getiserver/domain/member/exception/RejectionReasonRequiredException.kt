package team.inreok.getiserver.domain.member.exception

import team.inreok.getiserver.global.error.BusinessException

/**
 * 거절(REJECT) 처리인데 거절 사유(reason)가 비어 있는 경우다(Issue #99). 거절은 사유를 반드시
 * 기록하도록 정책상 필수 검증한다.
 */
class RejectionReasonRequiredException : BusinessException(MemberErrorCode.MEMBER_REJECTION_REASON_REQUIRED)
