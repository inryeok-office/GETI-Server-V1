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

        // Major 전체 교체(PATCH /me/majors)는 중복 majorId를 DUPLICATE_MAJOR(409)로 거부하지만,
        // 이 API의 명세에는 대응하는 중복 Error Code가 없어 여기서는 중복을 조용히 제거하고
        // 정상 처리한다(두 API의 의도된 차이, 실수 아님).
        val distinctIds = techStackIds.distinct()
        val techStacks = techStackRepository.findAllById(distinctIds)
        if (techStacks.size != distinctIds.size) throw TechStackNotFoundException(distinctIds)

        memberTechStackRepository.deleteAllByIdMemberId(memberId)
        memberTechStackRepository.saveAll(distinctIds.map { MemberTechStack(MemberTechStackId(memberId, it)) })

        val items = techStacks.sortedBy { it.name }.map(TechStackResponse::from)
        return MemberTechStacksResponse(items)
    }
}
