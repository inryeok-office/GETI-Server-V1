package team.inreok.getiserver.domain.member.service

import org.springframework.data.domain.Pageable
import team.inreok.getiserver.domain.member.dto.MemberSearchResponse
import team.inreok.getiserver.domain.member.entity.type.AcademicStatus
import team.inreok.getiserver.domain.member.entity.type.DepartmentType

interface MemberSearchService {
    fun search(
        name: String?,
        academicStatus: AcademicStatus?,
        cohort: Int?,
        department: DepartmentType?,
        majorId: Long?,
        techStackId: Long?,
        pageable: Pageable,
    ): MemberSearchResponse
}
