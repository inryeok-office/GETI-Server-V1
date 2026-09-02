package team.inreok.getiserver.domain.member.exception

import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.global.error.BusinessException

/**
 * 승인 대기(PENDING)가 아닌 회원을 교직원 승인·거절 API로 처리하려 한 경우다(Issue #99). 승인/거절은
 * PENDING 상태에서만 허용되며, 이미 승인/거절됐거나 다른 상태이면 409로 거부한다. 상태 값은 우리
 * 코드가 작성한 안전한 문구이므로 Message에 포함한다.
 */
class MemberNotPendingException(
    status: MemberStatus,
) : BusinessException(
        MemberErrorCode.MEMBER_NOT_PENDING,
        "승인 대기 상태의 회원만 처리할 수 있습니다. (status=$status)",
    )
