package team.inreok.getiserver.domain.member.service

import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.member.dto.MemberSearchItemResponse
import team.inreok.getiserver.domain.member.dto.MemberSearchResponse
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.type.AcademicStatus
import team.inreok.getiserver.domain.member.entity.type.DepartmentType
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

        val page =
            memberRepository.search(
                escapeLikePattern(name),
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
