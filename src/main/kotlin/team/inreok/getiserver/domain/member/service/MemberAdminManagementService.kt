package team.inreok.getiserver.domain.member.service

import org.springframework.data.domain.Pageable
import team.inreok.getiserver.domain.member.dto.AdminMemberDetailResponse
import team.inreok.getiserver.domain.member.dto.AdminMemberSearchResponse
import team.inreok.getiserver.domain.member.entity.type.DepartmentType
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.RoleType

/**
 * 관리자 회원 관리(Role/Status 무관 목록·상세 조회, Role Set 교체, 계정 상태 변경) Use Case다
 * (Issue #183). [MemberApprovalService]가 승인 대기(PENDING) 교직원의 승인·거절이라는 좁은 전용
 * Flow를 다루는 것과 달리, 이 Service는 이미 승인이 끝난 회원을 포함해 모든 회원을 대상으로 하는
 * 더 넓은 관리 기능이라 별도 Interface로 분리한다.
 */
interface MemberAdminManagementService {
    /**
     * Role/계정 상태를 가리지 않고 회원을 검색·목록 조회한다. name/status/role/cohort/department
     * 모두 선택이며, 지정하지 않은 Filter는 적용하지 않는다(AND로 결합).
     */
    fun search(
        name: String?,
        status: MemberStatus?,
        role: RoleType?,
        cohort: Int?,
        department: DepartmentType?,
        pageable: Pageable,
    ): AdminMemberSearchResponse

    /**
     * @throws team.inreok.getiserver.domain.member.exception.MemberNotFoundException 회원이 없을 때.
     */
    fun getDetail(memberId: Long): AdminMemberDetailResponse

    /**
     * 이 회원이 가질 최종 Role 집합으로 전체를 교체한다(기존 Role 중 없는 것은 회수, 새로 있는 것은
     * 부여).
     *
     * @throws team.inreok.getiserver.domain.member.exception.MemberSelfModificationForbiddenException
     *   memberId가 requesterMemberId 본인일 때.
     * @throws team.inreok.getiserver.domain.member.exception.MemberNotFoundException 회원이 없을 때.
     */
    fun updateRoles(
        memberId: Long,
        requesterMemberId: Long,
        roles: Set<RoleType>,
    ): AdminMemberDetailResponse

    /**
     * 계정 상태를 변경한다. ACTIVE <-> SUSPENDED 전이만 허용한다.
     *
     * @throws team.inreok.getiserver.domain.member.exception.MemberSelfModificationForbiddenException
     *   memberId가 requesterMemberId 본인일 때.
     * @throws team.inreok.getiserver.domain.member.exception.MemberNotFoundException 회원이 없을 때.
     * @throws team.inreok.getiserver.domain.member.exception.MemberStatusTransitionNotAllowedException
     *   ACTIVE <-> SUSPENDED가 아닌 전이일 때.
     */
    fun updateStatus(
        memberId: Long,
        requesterMemberId: Long,
        status: MemberStatus,
    ): AdminMemberDetailResponse
}
