package team.inreok.getiserver.domain.member.exception

import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.global.error.BusinessException

/**
 * 교직원 승인 대상이 아닌 회원을 교직원 승인 API로 처리하려 한 경우다(Issue #99). 교직원은 Google
 * OAuth로 가입하므로 승인 대상은 `OAuthProvider.GOOGLE`인 회원뿐이다. DG 학생이 비정상적으로 PENDING
 * 상태로 존재하더라도 이 API로 TEACHER가 되어서는 안 되므로 Provider를 함께 검증한다. Provider 값은
 * 우리 코드가 작성한 안전한 문구이므로 Message에 포함한다.
 */
class MemberNotApprovalTargetException(
    provider: OAuthProvider,
) : BusinessException(
        MemberErrorCode.MEMBER_NOT_APPROVAL_TARGET,
        "교직원 승인 대상 회원이 아닙니다. (provider=$provider)",
    )
