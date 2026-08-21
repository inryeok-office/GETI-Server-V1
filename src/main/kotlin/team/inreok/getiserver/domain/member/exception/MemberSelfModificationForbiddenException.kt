package team.inreok.getiserver.domain.member.exception

import team.inreok.getiserver.global.error.BusinessException

/**
 * 관리자가 자기 자신을 대상으로 Role Set 교체 또는 계정 상태 변경을 시도한 경우다(Issue #183).
 */
class MemberSelfModificationForbiddenException :
    BusinessException(
        MemberErrorCode.MEMBER_SELF_MODIFICATION_FORBIDDEN,
        "자기 자신의 Role이나 계정 상태는 스스로 변경할 수 없습니다.",
    )
