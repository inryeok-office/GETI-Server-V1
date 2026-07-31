package team.inreok.getiserver.domain.member.dto

import team.inreok.getiserver.domain.member.entity.type.DepartmentType

data class MemberSearchResponse(
    val content: List<MemberSearchItemResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val first: Boolean,
    val last: Boolean,
)

data class MemberSearchItemResponse(
    val memberId: Long,
    val name: String,
    val profileImageUrl: String?,
    val cohort: Int?,
    val department: DepartmentType?,
    val isPublic: Boolean,
)
