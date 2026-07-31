package team.inreok.getiserver.domain.member.dto

import team.inreok.getiserver.domain.member.entity.TechStack
import team.inreok.getiserver.domain.member.entity.type.TechStackCategory

data class TechStackListResponse(
    val items: List<TechStackResponse>,
)

data class TechStackResponse(
    val techStackId: Long,
    val name: String,
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
