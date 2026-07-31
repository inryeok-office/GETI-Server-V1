package team.inreok.getiserver.domain.member.dto

import team.inreok.getiserver.domain.member.entity.Major

data class MajorListResponse(
    val items: List<MajorResponse>,
)

data class MajorResponse(
    val majorId: Long,
    val name: String,
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
