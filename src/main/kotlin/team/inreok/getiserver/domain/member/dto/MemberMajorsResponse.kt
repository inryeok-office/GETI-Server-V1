package team.inreok.getiserver.domain.member.dto

data class MemberMajorsResponse(
    val majors: List<MemberMajorItemResponse>,
)

data class MemberMajorItemResponse(
    val majorId: Long,
    val name: String,
)

data class MajorIdsRequest(
    val majorIds: List<Long>,
)
