package team.inreok.getiserver.domain.member.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "기술 스택 전체 교체 응답")
data class MemberTechStacksResponse(
    @param:Schema(description = "교체 후 선택된 기술 스택 목록(이름순)")
    val techStacks: List<TechStackResponse>,
)

@Schema(description = "기술 스택 전체 교체 요청")
data class TechStackIdsRequest(
    @param:Schema(
        description =
            "선택할 기술 스택 ID 목록(전체 교체, 기존 선택은 모두 삭제 후 저장). " +
                "중복 ID는 조용히 제거된다. 빈 배열이면 선택이 모두 해제된다.",
        example = "[10, 11]",
    )
    val techStackIds: List<Long>,
)
