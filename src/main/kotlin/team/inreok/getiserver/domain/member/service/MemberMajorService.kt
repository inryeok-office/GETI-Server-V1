package team.inreok.getiserver.domain.member.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.member.dto.MemberMajorItemResponse
import team.inreok.getiserver.domain.member.dto.MemberMajorsResponse
import team.inreok.getiserver.domain.member.entity.MemberMajor
import team.inreok.getiserver.domain.member.entity.MemberMajorId
import team.inreok.getiserver.domain.member.exception.DuplicateMajorException
import team.inreok.getiserver.domain.member.exception.MajorNotFoundException
import team.inreok.getiserver.domain.member.exception.MemberNotFoundException
import team.inreok.getiserver.domain.member.repository.MajorRepository
import team.inreok.getiserver.domain.member.repository.MemberMajorRepository
import team.inreok.getiserver.domain.member.repository.MemberRepository

@Service
class MemberMajorService(
    private val memberRepository: MemberRepository,
    private val majorRepository: MajorRepository,
    private val memberMajorRepository: MemberMajorRepository,
) {
    @Transactional
    fun replaceAll(
        memberId: Long,
        majorIds: List<Long>,
    ): MemberMajorsResponse {
        requireMemberExists(memberId)
        requireNoDuplicates(majorIds)
        val majors = findAllMajorsOrThrow(majorIds)

        memberMajorRepository.deleteAllByIdMemberId(memberId)
        memberMajorRepository.saveAll(majorIds.map { MemberMajor(MemberMajorId(memberId, it)) })

        val items =
            majors.sortedBy { it.name }.map {
                MemberMajorItemResponse(requireNotNull(it.id) { "저장된 Major는 id를 가져야 합니다." }, it.name)
            }
        return MemberMajorsResponse(items)
    }

    private fun requireMemberExists(memberId: Long) {
        if (!memberRepository.existsById(memberId)) throw MemberNotFoundException(memberId)
    }

    private fun requireNoDuplicates(majorIds: List<Long>) {
        if (majorIds.size != majorIds.toSet().size) throw DuplicateMajorException()
    }

    private fun findAllMajorsOrThrow(majorIds: List<Long>) =
        majorRepository.findAllById(majorIds).also {
            if (it.size != majorIds.size) throw MajorNotFoundException(majorIds)
        }
}
