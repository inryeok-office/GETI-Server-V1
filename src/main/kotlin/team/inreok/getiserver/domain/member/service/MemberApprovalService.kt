package team.inreok.getiserver.domain.member.service

import team.inreok.getiserver.domain.member.dto.MemberApprovalRequest
import team.inreok.getiserver.domain.member.dto.MemberApprovalResponse

/**
 * 교직원 가입 승인·거절 Use Case다(Issue #99). 승인/거절은 회원의 비즈니스 상태 변경이므로 Member
 * 도메인이 소유한다. `auth`는 승인 이후 Refresh 시 [OAuthMemberPort][team.inreok.getiserver.domain.member.query.OAuthMemberPort]로
 * 최신 Role을 다시 조회하므로, 이 Service는 Token을 직접 다루지 않고 상태/Role만 원자적으로 바꾼다.
 */
interface MemberApprovalService {
    /**
     * PENDING 상태의 교직원(GOOGLE) 회원을 승인하거나 거절한다.
     *
     * 승인(APPROVE): status를 ACTIVE로 바꾸고 approvedAt을 기록하며 TEACHER Role을 부여한다.
     * 거절(REJECT): status를 REJECTED로 바꾸고 거절 사유를 기록한다. 상태 변경과 Role 부여는 하나의
     * Transaction에서 이뤄져 부분 성공이 남지 않는다.
     *
     * @throws team.inreok.getiserver.domain.member.exception.MemberNotFoundException 회원이 없을 때.
     * @throws team.inreok.getiserver.domain.member.exception.MemberNotPendingException 회원이 PENDING이 아닐 때.
     * @throws team.inreok.getiserver.domain.member.exception.MemberNotApprovalTargetException 교직원(GOOGLE) 승인 대상이 아닐 때.
     * @throws team.inreok.getiserver.domain.member.exception.RejectionReasonRequiredException 거절인데 사유가 비어 있을 때.
     */
    fun process(
        memberId: Long,
        request: MemberApprovalRequest,
    ): MemberApprovalResponse
}
