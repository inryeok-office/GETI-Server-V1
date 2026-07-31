package team.inreok.getiserver.domain.member.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.member.dto.TechStackListResponse
import team.inreok.getiserver.domain.member.dto.TechStackResponse
import team.inreok.getiserver.domain.member.entity.type.TechStackCategory
import team.inreok.getiserver.domain.member.repository.TechStackRepository
import team.inreok.getiserver.domain.member.service.TechStackService
import team.inreok.getiserver.domain.member.service.escapeLikePattern

@Service
class TechStackServiceImpl(
    private val techStackRepository: TechStackRepository,
) : TechStackService {
    @Transactional(readOnly = true)
    override fun search(
        query: String?,
        category: TechStackCategory?,
    ): TechStackListResponse {
        val items = techStackRepository.search(query?.let(::escapeLikePattern), category).map(TechStackResponse::from)
        return TechStackListResponse(items)
    }
}
