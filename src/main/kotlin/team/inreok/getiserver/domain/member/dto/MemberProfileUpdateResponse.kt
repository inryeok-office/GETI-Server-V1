package team.inreok.getiserver.domain.member.dto

import team.inreok.getiserver.domain.member.entity.type.DepartmentType
import java.time.LocalDateTime

data class MemberProfileUpdateResponse(
    val memberId: Long,
    val name: String,
    val department: DepartmentType?,
    val phone: String?,
    val desiredJob: String?,
    val bio: String?,
    val githubUrl: String?,
    val isPublic: Boolean,
    val profileImageUrl: String?,
    val updatedAt: LocalDateTime,
)
