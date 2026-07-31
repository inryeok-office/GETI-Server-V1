package team.inreok.getiserver.domain.member.dto

import team.inreok.getiserver.domain.member.entity.type.AcademicStatus
import team.inreok.getiserver.domain.member.entity.type.DepartmentType
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.RoleType

data class MyProfileResponse(
    val memberId: Long,
    val name: String,
    val email: String,
    val roles: List<RoleType>,
    val status: MemberStatus,
    val academicStatus: AcademicStatus?,
    val cohort: Int?,
    val department: DepartmentType?,
    val phone: String?,
    val profileImageUrl: String?,
    val desiredJob: String?,
    val bio: String?,
    val githubUrl: String?,
    val isPublic: Boolean,
    val majors: List<String>,
    val techStacks: List<String>,
)
