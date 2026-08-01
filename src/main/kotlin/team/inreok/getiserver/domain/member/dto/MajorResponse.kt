package team.inreok.getiserver.domain.member.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.member.entity.Major

@Schema(description = "전공 메타데이터 목록 응답")
data class MajorListResponse(
    @param:Schema(description = "전공 목록(이름순)")
    val items: List<MajorResponse>,
)

@Schema(description = "전공 메타데이터")
data class MajorResponse(
    @param:Schema(description = "전공 ID", example = "1")
    val majorId: Long,
    @param:Schema(description = "전공명", example = "소프트웨어")
    val name: String,
    @param:Schema(description = "폐지 여부(false면 선택 목록에는 남아있지만 새로 선택할 수 없음)", example = "true")
    val active: Boolean,
) {
    companion object {
        fun from(major: Major): MajorResponse =
            MajorResponse(
                majorId = requireNotNull(major.id) { "저장된 Major는 id를 가져야 합니다." },
                name = major.name,
                active = major.active,
            )
    }
}
