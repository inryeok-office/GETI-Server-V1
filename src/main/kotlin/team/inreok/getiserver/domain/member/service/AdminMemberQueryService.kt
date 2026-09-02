package team.inreok.getiserver.domain.member.service

import team.inreok.getiserver.domain.member.dto.AdminMemberListResponse
import team.inreok.getiserver.domain.member.entity.type.RoleType

interface AdminMemberQueryService {
    /**
     * 지정한 [role]을 가진 ACTIVE 회원을 이름순으로 조회한다(Issue #182, 담당자 선택 Dropdown용).
     * 응답은 `memberId`/`name`만 담아 민감 정보를 노출하지 않는다.
     */
    fun listActiveMembersByRole(role: RoleType): AdminMemberListResponse
}
