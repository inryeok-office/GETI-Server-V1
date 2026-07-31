package team.inreok.getiserver.domain.member.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.member.repository.MajorRepository
import team.inreok.getiserver.domain.member.repository.MemberMajorRepository
import team.inreok.getiserver.domain.member.repository.MemberTechStackRepository
import team.inreok.getiserver.domain.member.repository.TechStackRepository

/**
 * Member의 전공/기술 스택 선택(member_majors, member_tech_stacks)을 이름 목록으로 조회한다.
 * Profile 조회 응답(GET /members/{id}, GET /me/profile 등)이 공통으로 필요로 하는 조회 전용 로직이다.
 */
@Service
class MemberSelectionQueryService(
    private val memberMajorRepository: MemberMajorRepository,
    private val majorRepository: MajorRepository,
    private val memberTechStackRepository: MemberTechStackRepository,
    private val techStackRepository: TechStackRepository,
) {
    @Transactional(readOnly = true)
    fun getMajorNames(memberId: Long): List<String> {
        val majorIds = memberMajorRepository.findAllByIdMemberId(memberId).map { it.id.majorId }
        if (majorIds.isEmpty()) return emptyList()
        return majorRepository.findAllById(majorIds).map { it.name }.sorted()
    }

    @Transactional(readOnly = true)
    fun getTechStackNames(memberId: Long): List<String> {
        val techStackIds = memberTechStackRepository.findAllByIdMemberId(memberId).map { it.id.techStackId }
        if (techStackIds.isEmpty()) return emptyList()
        return techStackRepository.findAllById(techStackIds).map { it.name }.sorted()
    }
}
