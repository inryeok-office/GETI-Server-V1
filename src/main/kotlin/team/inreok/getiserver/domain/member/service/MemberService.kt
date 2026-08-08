package team.inreok.getiserver.domain.member.service

import team.inreok.getiserver.domain.member.dto.MemberProfileResponse
import team.inreok.getiserver.domain.member.dto.MemberProfileUpdateResponse
import team.inreok.getiserver.domain.member.dto.MyProfileResponse
import tools.jackson.databind.JsonNode

interface MemberService {
    /**
     * [requesterId]는 프로필 이미지 URL 발급 권한을 판정하는 데 쓴다. 비공개 프로필의 이미지는
     * 본인이 아닌 요청자에게 `null`로 내려간다(`MemberProfileImageAccessChecker`).
     */
    fun getProfile(
        memberId: Long,
        requesterId: Long,
    ): MemberProfileResponse

    fun getMyProfile(memberId: Long): MyProfileResponse

    fun updateProfile(
        memberId: Long,
        body: JsonNode,
    ): MemberProfileUpdateResponse
}
