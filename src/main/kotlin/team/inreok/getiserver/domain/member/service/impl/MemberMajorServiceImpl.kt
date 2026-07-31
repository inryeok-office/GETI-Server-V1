package team.inreok.getiserver.domain.member.service.impl

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
import team.inreok.getiserver.domain.member.service.MemberMajorService

@Service
class MemberMajorServiceImpl(
    private val memberRepository: MemberRepository,
    private val majorRepository: MajorRepository,
    private val memberMajorRepository: MemberMajorRepository,
) : MemberMajorService {
    @Transactional
    override fun replaceAll(
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

    // 폐지(active=false)된 전공은 새로 선택할 수 없다(코드 리뷰 Minor 반영, 사용자 결정: 비활성 전공
    // 선택 차단). active=false인 majorId는 존재하지 않는 것과 동일하게 MAJOR_NOT_FOUND로 처리된다.
    private fun findAllMajorsOrThrow(majorIds: List<Long>) =
        majorRepository.findAllByIdInAndActiveTrue(majorIds).also {
            if (it.size != majorIds.size) throw MajorNotFoundException(majorIds)
        }
}
