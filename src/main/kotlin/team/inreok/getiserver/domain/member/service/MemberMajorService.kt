package team.inreok.getiserver.domain.member.service

import team.inreok.getiserver.domain.member.dto.MemberMajorsResponse

interface MemberMajorService {
    fun replaceAll(
        memberId: Long,
        majorIds: List<Long>,
    ): MemberMajorsResponse
}
