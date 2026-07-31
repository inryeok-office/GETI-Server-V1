package team.inreok.getiserver.domain.member.service

import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.member.dto.MemberSearchItemResponse
import team.inreok.getiserver.domain.member.dto.MemberSearchResponse
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.type.AcademicStatus
import team.inreok.getiserver.domain.member.entity.type.DepartmentType
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.exception.NameRequiredException
import team.inreok.getiserver.domain.member.repository.MemberRepository

@Service
class MemberSearchService(
    private val memberRepository: MemberRepository,
) {
    @Transactional(readOnly = true)
    fun search(
        name: String?,
        academicStatus: AcademicStatus?,
        cohort: Int?,
        department: DepartmentType?,
        majorId: Long?,
        techStackId: Long?,
        pageable: Pageable,
    ): MemberSearchResponse {
        if (name.isNullOrBlank()) throw NameRequiredException()

        // 이 API는 "학생 이름 검색"이므로 재학 여부(academicStatus)와 무관하게 STUDENT Role이면서
        // ACTIVE 상태(승인 완료, 탈퇴/정지/거절/승인대기 제외)인 회원만 검색 대상으로 삼는다
        // (코드 리뷰 Major 반영).
        val page =
            memberRepository.search(
                escapeLikePattern(name),
                MemberStatus.ACTIVE,
                RoleType.STUDENT,
                academicStatus,
                cohort,
                department,
                majorId,
                techStackId,
                pageable,
            )
        return MemberSearchResponse(
            content = page.content.map(::toItem),
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            first = page.isFirst,
            last = page.isLast,
        )
    }

    private fun toItem(member: Member): MemberSearchItemResponse =
        MemberSearchItemResponse(
            memberId = requireNotNull(member.id) { "저장된 Member는 id를 가져야 합니다." },
            name = member.name.orEmpty(),
            profileImageUrl = null,
            cohort = member.cohort,
            department = member.department,
            isPublic = member.profilePublic,
        )
}
