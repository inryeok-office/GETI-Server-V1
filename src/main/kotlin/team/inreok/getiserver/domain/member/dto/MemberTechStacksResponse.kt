package team.inreok.getiserver.domain.member.dto

data class MemberTechStacksResponse(
    val techStacks: List<TechStackResponse>,
)

data class TechStackIdsRequest(
    val techStackIds: List<Long>,
)
