package team.inreok.getiserver.domain.member.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "전공 전체 교체 응답")
data class MemberMajorsResponse(
    @param:Schema(description = "교체 후 선택된 전공 목록(이름순)")
    val majors: List<MemberMajorItemResponse>,
)

@Schema(description = "선택된 전공 항목")
data class MemberMajorItemResponse(
    @param:Schema(description = "전공 ID", example = "1")
    val majorId: Long,
    @param:Schema(description = "전공명", example = "소프트웨어")
    val name: String,
)

@Schema(description = "전공 전체 교체 요청")
data class MajorIdsRequest(
    @param:Schema(
        description = "선택할 전공 ID 목록(전체 교체, 기존 선택은 모두 삭제 후 저장). 빈 배열이면 전공 선택이 모두 해제된다.",
        example = "[1, 2]",
    )
    val majorIds: List<Long>,
)
