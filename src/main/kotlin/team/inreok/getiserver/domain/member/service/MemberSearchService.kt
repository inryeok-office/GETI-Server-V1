package team.inreok.getiserver.domain.member.service

import org.springframework.data.domain.Pageable
import team.inreok.getiserver.domain.member.dto.MemberSearchResponse
import team.inreok.getiserver.domain.member.entity.type.AcademicStatus
import team.inreok.getiserver.domain.member.entity.type.DepartmentType

interface MemberSearchService {
    /**
     * [requesterId]는 목록의 프로필 이미지 URL 발급 권한 판정에 쓴다. 비공개 프로필 회원의
     * `profileImageUrl`은 본인이 아닌 요청자에게 `null`로 내려간다.
     */
    fun search(
        requesterId: Long,
        name: String?,
        academicStatus: AcademicStatus?,
        cohort: Int?,
        department: DepartmentType?,
        majorId: Long?,
        techStackId: Long?,
        pageable: Pageable,
    ): MemberSearchResponse
}
