package team.inreok.getiserver.domain.member.exception

import team.inreok.getiserver.global.error.BusinessException

/**
 * 가입이 거절된(REJECTED) 교직원이 재신청(intent=REAPPLY) 없이 OAuth 로그인을 시도한 경우다
 * (Issue #229). 로그인을 막되, `/staff/signup` 화면이 거절 사유를 표시할 수 있도록 관리자가 작성한
 * [rejectionReason]을 응답 Message로 전달한다 -- 이 값은 관리자가 사용자에게 보여주려고 작성한
 * 안전한 문구이므로 노출한다([BusinessException] Message 노출 정책). 사유가 비어 있으면 기본 문구를
 * 사용한다.
 */
class StaffSignupRejectedException(
    rejectionReason: String?,
) : BusinessException(
        MemberErrorCode.MEMBER_SIGNUP_REJECTED,
        rejectionReason?.takeIf { it.isNotBlank() } ?: MemberErrorCode.MEMBER_SIGNUP_REJECTED.defaultMessage,
    )
