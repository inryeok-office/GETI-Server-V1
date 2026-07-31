package team.inreok.getiserver.domain.member.service

import team.inreok.getiserver.domain.member.dto.MemberTechStacksResponse

interface MemberTechStackSelectionService {
    fun replaceAll(
        memberId: Long,
        techStackIds: List<Long>,
    ): MemberTechStacksResponse
}
