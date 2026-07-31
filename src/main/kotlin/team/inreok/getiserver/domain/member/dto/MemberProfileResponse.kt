package team.inreok.getiserver.domain.member.dto

import team.inreok.getiserver.domain.member.entity.type.DepartmentType

data class MemberProfileResponse(
    val memberId: Long,
    val name: String,
    val profileImageUrl: String?,
    val cohort: Int?,
    val department: DepartmentType?,
    val majors: List<String>,
    val techStacks: List<String>,
    val desiredJob: String?,
    val bio: String?,
    val isPublic: Boolean,
    val profileRestricted: Boolean,
)
