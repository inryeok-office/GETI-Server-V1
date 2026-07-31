package team.inreok.getiserver.domain.member.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.member.repository.MajorRepository
import team.inreok.getiserver.domain.member.repository.MemberMajorRepository
import team.inreok.getiserver.domain.member.repository.MemberTechStackRepository
import team.inreok.getiserver.domain.member.repository.TechStackRepository
import team.inreok.getiserver.domain.member.service.MemberSelectionQueryService

@Service
class MemberSelectionQueryServiceImpl(
    private val memberMajorRepository: MemberMajorRepository,
    private val majorRepository: MajorRepository,
    private val memberTechStackRepository: MemberTechStackRepository,
    private val techStackRepository: TechStackRepository,
) : MemberSelectionQueryService {
    @Transactional(readOnly = true)
    override fun getMajorNames(memberId: Long): List<String> {
        val majorIds = memberMajorRepository.findAllByIdMemberId(memberId).map { it.id.majorId }
        if (majorIds.isEmpty()) return emptyList()
        return majorRepository.findAllById(majorIds).map { it.name }.sorted()
    }

    @Transactional(readOnly = true)
    override fun getTechStackNames(memberId: Long): List<String> {
        val techStackIds = memberTechStackRepository.findAllByIdMemberId(memberId).map { it.id.techStackId }
        if (techStackIds.isEmpty()) return emptyList()
        return techStackRepository.findAllById(techStackIds).map { it.name }.sorted()
    }
}
