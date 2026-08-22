package team.inreok.getiserver.domain.member.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "프로필 링크 하나(Label + URL). 배열 순서가 그대로 표시 순서다.")
data class MemberProfileLinkResponse(
    @param:Schema(description = "링크 표시 이름", example = "기술 블로그")
    val label: String,
    @param:Schema(description = "링크 URL(http/https만 허용)", example = "https://blog.example.com/me")
    val url: String,
)
