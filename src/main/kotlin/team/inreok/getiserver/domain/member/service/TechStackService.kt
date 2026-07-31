package team.inreok.getiserver.domain.member.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.member.dto.TechStackListResponse
import team.inreok.getiserver.domain.member.dto.TechStackResponse
import team.inreok.getiserver.domain.member.entity.type.TechStackCategory
import team.inreok.getiserver.domain.member.repository.TechStackRepository

@Service
class TechStackService(
    private val techStackRepository: TechStackRepository,
) {
    @Transactional(readOnly = true)
    fun search(
        query: String?,
        category: TechStackCategory?,
    ): TechStackListResponse {
        val items = techStackRepository.search(query, category).map(TechStackResponse::from)
        return TechStackListResponse(items)
    }
}
