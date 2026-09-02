package team.inreok.getiserver.domain.member.exception

import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.global.error.BusinessException

/**
 * 계정 상태 변경(PATCH .../status)이 허용되지 않는 전이일 때다(Issue #183 정책 확정: 관리자가 수동으로
 * 전이할 수 있는 것은 ACTIVE <-> SUSPENDED뿐이다). 상태 값은 우리 코드가 작성한 안전한 문구이므로
 * Message에 포함한다.
 */
class MemberStatusTransitionNotAllowedException(
    from: MemberStatus,
    to: MemberStatus,
) : BusinessException(
        MemberErrorCode.MEMBER_STATUS_TRANSITION_NOT_ALLOWED,
        "현재 상태에서는 요청한 상태로 변경할 수 없습니다. (from=$from, to=$to)",
    )
