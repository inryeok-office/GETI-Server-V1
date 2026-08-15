package team.inreok.getiserver.domain.member.exception

import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.global.error.BusinessException

/**
 * 로그인이 허용되지 않는 상태(REJECTED/SUSPENDED/WITHDRAWN)의 기존 회원이 OAuth로 재로그인을
 * 시도한 경우다(Issue #104). 상태 값은 우리 코드가 작성한 안전한 문구이므로 Message에 포함한다.
 */
class MemberLoginNotAllowedException(
    status: MemberStatus,
) : BusinessException(
        MemberErrorCode.MEMBER_LOGIN_NOT_ALLOWED,
        "로그인이 허용되지 않는 회원 상태입니다. (status=$status)",
    )
