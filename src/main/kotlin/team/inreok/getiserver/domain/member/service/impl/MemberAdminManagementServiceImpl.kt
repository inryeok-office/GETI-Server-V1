package team.inreok.getiserver.domain.member.service.impl

import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.member.dto.AdminMemberDetailResponse
import team.inreok.getiserver.domain.member.dto.AdminMemberSearchItemResponse
import team.inreok.getiserver.domain.member.dto.AdminMemberSearchResponse
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.MemberRole
import team.inreok.getiserver.domain.member.entity.MemberRoleId
import team.inreok.getiserver.domain.member.entity.type.DepartmentType
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.exception.MemberNotFoundException
import team.inreok.getiserver.domain.member.exception.MemberSelfModificationForbiddenException
import team.inreok.getiserver.domain.member.exception.MemberStatusTransitionNotAllowedException
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.member.repository.MemberRoleRepository
import team.inreok.getiserver.domain.member.service.MemberAdminManagementService
import team.inreok.getiserver.domain.member.service.escapeLikePattern

/**
 * [MemberAdminManagementService]의 구현이다(Issue #183).
 */
@Service
class MemberAdminManagementServiceImpl(
    private val memberRepository: MemberRepository,
    private val memberRoleRepository: MemberRoleRepository,
) : MemberAdminManagementService {
    @Transactional(readOnly = true)
    override fun search(
        name: String?,
        status: MemberStatus?,
        role: RoleType?,
        cohort: Int?,
        department: DepartmentType?,
        pageable: Pageable,
    ): AdminMemberSearchResponse {
        // 검색어를 보내지 않은 경우와 공백만 보낸 경우를 모두 "검색어 없음"으로 취급한다
        // (MemberSearchServiceImpl/InquiryServiceImpl.listAdmin과 동일한 관례).
        val trimmedName = name?.trim()?.takeIf { it.isNotEmpty() }
        val page =
            memberRepository.searchForAdmin(
                hasName = trimmedName != null,
                name = trimmedName?.let(::escapeLikePattern) ?: "",
                status = status,
                role = role,
                cohort = cohort,
                department = department,
                pageable = pageable,
            )

        // 항목마다 Role을 개별 조회하면 Page 항목 수만큼 Query가 늘어난다(N+1). 이번 Page에 등장하는
        // memberId 전체를 모아 한 번에 배치 조회한다(JobApplicationAdminServiceImpl과 동일한 원칙).
        val memberIds = page.content.mapNotNull { it.id }.toSet()
        val rolesByMemberId =
            if (memberIds.isEmpty()) {
                emptyMap()
            } else {
                memberRoleRepository.findAllByIdMemberIdIn(memberIds).groupBy({ it.id.memberId }, { it.id.role })
            }

        return AdminMemberSearchResponse(
            content = page.content.map { toSearchItem(it, rolesByMemberId[it.id].orEmpty().toSet()) },
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            first = page.isFirst,
            last = page.isLast,
        )
    }

    @Transactional(readOnly = true)
    override fun getDetail(memberId: Long): AdminMemberDetailResponse {
        val member = memberRepository.findById(memberId).orElseThrow { MemberNotFoundException(memberId) }
        val roles = rolesOf(memberId)
        return toDetail(member, roles)
    }

    @Transactional
    override fun updateRoles(
        memberId: Long,
        requesterMemberId: Long,
        roles: Set<RoleType>,
    ): AdminMemberDetailResponse {
        requireNotSelf(memberId, requesterMemberId)
        // 두 관리자가 같은 회원의 Role을 동시에 교체해도 Row Lock으로 순차 처리되어(교직원
        // 승인·거절과 동일한 관례, MemberRepository.findByIdForUpdate KDoc 참고) 삭제·삽입이
        // 서로 뒤섞이지 않는다.
        val member = memberRepository.findByIdForUpdate(memberId) ?: throw MemberNotFoundException(memberId)

        memberRoleRepository.deleteAllByIdMemberId(memberId)
        if (roles.isNotEmpty()) {
            memberRoleRepository.saveAll(roles.map { MemberRole(MemberRoleId(memberId = memberId, role = it)) })
        }

        return toDetail(member, roles)
    }

    @Transactional
    override fun updateStatus(
        memberId: Long,
        requesterMemberId: Long,
        status: MemberStatus,
    ): AdminMemberDetailResponse {
        requireNotSelf(memberId, requesterMemberId)
        // Row Lock을 먼저 잡은 뒤 현재 상태를 기준으로 전이를 검증한다. 동시에 들어온 두 요청 중
        // 나중 Transaction은 앞선 요청이 이미 반영한 최신 상태를 보고 검증하므로(교직원 승인·거절과
        // 동일한 관례), 예를 들어 두 요청이 모두 ACTIVE -> SUSPENDED를 시도하면 하나만 성공하고
        // 나머지는 "이미 SUSPENDED"라는 이유로 409(MEMBER_STATUS_TRANSITION_NOT_ALLOWED)로 거부된다.
        val member = memberRepository.findByIdForUpdate(memberId) ?: throw MemberNotFoundException(memberId)

        val allowed =
            (member.status == MemberStatus.ACTIVE && status == MemberStatus.SUSPENDED) ||
                (member.status == MemberStatus.SUSPENDED && status == MemberStatus.ACTIVE)
        if (!allowed) throw MemberStatusTransitionNotAllowedException(member.status, status)
        member.status = status

        return toDetail(member, rolesOf(memberId))
    }

    private fun requireNotSelf(
        memberId: Long,
        requesterMemberId: Long,
    ) {
        if (memberId == requesterMemberId) throw MemberSelfModificationForbiddenException()
    }

    private fun rolesOf(memberId: Long): Set<RoleType> =
        memberRoleRepository.findAllByIdMemberId(memberId).map { it.id.role }.toSet()

    private fun toSearchItem(
        member: Member,
        roles: Set<RoleType>,
    ) = AdminMemberSearchItemResponse(
        memberId = requireNotNull(member.id) { "저장된 Member는 id를 가져야 합니다." },
        email = member.email,
        name = member.name,
        status = member.status,
        roles = roles,
        oauthProvider = member.oauthProvider,
        cohort = member.cohort,
        department = member.department,
        createdAt = requireNotNull(member.createdAt) { "저장된 Member는 createdAt을 가져야 합니다." },
    )

    private fun toDetail(
        member: Member,
        roles: Set<RoleType>,
    ) = AdminMemberDetailResponse(
        memberId = requireNotNull(member.id) { "저장된 Member는 id를 가져야 합니다." },
        email = member.email,
        name = member.name,
        status = member.status,
        roles = roles,
        oauthProvider = member.oauthProvider,
        academicStatus = member.academicStatus,
        cohort = member.cohort,
        grade = member.grade,
        department = member.department,
        phoneNumber = member.phoneNumber,
        githubUrl = member.githubUrl,
        rejectionReason = member.rejectionReason,
        approvedAt = member.approvedAt,
        createdAt = requireNotNull(member.createdAt) { "저장된 Member는 createdAt을 가져야 합니다." },
        updatedAt = requireNotNull(member.updatedAt) { "저장된 Member는 updatedAt을 가져야 합니다." },
        withdrawnAt = member.withdrawnAt,
    )
}
