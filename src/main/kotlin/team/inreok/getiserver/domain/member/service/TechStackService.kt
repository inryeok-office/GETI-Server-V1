package team.inreok.getiserver.domain.member.service

import team.inreok.getiserver.domain.member.dto.TechStackListResponse
import team.inreok.getiserver.domain.member.entity.type.TechStackCategory

interface TechStackService {
    fun search(
        query: String?,
        category: TechStackCategory?,
    ): TechStackListResponse
}
