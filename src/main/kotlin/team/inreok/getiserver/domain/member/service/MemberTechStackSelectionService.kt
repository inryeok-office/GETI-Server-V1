package team.inreok.getiserver.domain.member.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.member.dto.MemberTechStacksResponse
import team.inreok.getiserver.domain.member.dto.TechStackResponse
import team.inreok.getiserver.domain.member.entity.MemberTechStack
import team.inreok.getiserver.domain.member.entity.MemberTechStackId
import team.inreok.getiserver.domain.member.exception.MemberNotFoundException
import team.inreok.getiserver.domain.member.exception.TechStackNotFoundException
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.member.repository.MemberTechStackRepository
import team.inreok.getiserver.domain.member.repository.TechStackRepository

@Service
class MemberTechStackSelectionService(
    private val memberRepository: MemberRepository,
    private val techStackRepository: TechStackRepository,
    private val memberTechStackRepository: MemberTechStackRepository,
) {
    @Transactional
    fun replaceAll(
        memberId: Long,
        techStackIds: List<Long>,
    ): MemberTechStacksResponse {
        if (!memberRepository.existsById(memberId)) throw MemberNotFoundException(memberId)

        val distinctIds = techStackIds.distinct()
        val techStacks = techStackRepository.findAllById(distinctIds)
        if (techStacks.size != distinctIds.size) throw TechStackNotFoundException(distinctIds)

        memberTechStackRepository.deleteAllByIdMemberId(memberId)
        memberTechStackRepository.saveAll(distinctIds.map { MemberTechStack(MemberTechStackId(memberId, it)) })

        val items = techStacks.sortedBy { it.name }.map(TechStackResponse::from)
        return MemberTechStacksResponse(items)
    }
}
