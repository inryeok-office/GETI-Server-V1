package team.inreok.getiserver.domain.member.service

import team.inreok.getiserver.domain.member.dto.MemberProfileResponse
import team.inreok.getiserver.domain.member.dto.MemberProfileUpdateResponse
import team.inreok.getiserver.domain.member.dto.MyProfileResponse
import tools.jackson.databind.JsonNode

interface MemberService {
    fun getProfile(memberId: Long): MemberProfileResponse

    fun getMyProfile(memberId: Long): MyProfileResponse

    fun updateProfile(
        memberId: Long,
        body: JsonNode,
    ): MemberProfileUpdateResponse
}
