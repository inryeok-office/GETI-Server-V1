package team.inreok.getiserver.domain.member.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.member.entity.TechStack
import team.inreok.getiserver.domain.member.entity.type.TechStackCategory

@Schema(description = "기술 스택 메타데이터 목록 응답")
data class TechStackListResponse(
    @param:Schema(description = "기술 스택 목록(이름순)")
    val items: List<TechStackResponse>,
)

@Schema(description = "기술 스택 메타데이터")
data class TechStackResponse(
    @param:Schema(description = "기술 스택 ID", example = "10")
    val techStackId: Long,
    @param:Schema(description = "기술 스택명", example = "Kotlin")
    val name: String,
    @param:Schema(description = "분류")
    val category: TechStackCategory,
) {
    companion object {
        fun from(techStack: TechStack): TechStackResponse =
            TechStackResponse(
                techStackId = requireNotNull(techStack.id) { "저장된 TechStack은 id를 가져야 합니다." },
                name = techStack.name,
                category = techStack.category,
            )
    }
}
