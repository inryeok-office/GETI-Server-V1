package team.inreok.getiserver.domain.member.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.member.query.InquiryAuthorSearchQueryPort
import team.inreok.getiserver.domain.member.repository.MemberRepository

/** `inquiry` Module에 공개된 조회 계약([InquiryAuthorSearchQueryPort])의 구현이다. */
@Service
class InquiryAuthorSearchQueryPortImpl(
    private val memberRepository: MemberRepository,
) : InquiryAuthorSearchQueryPort {
    @Transactional(readOnly = true)
    override fun findIdsByNameContaining(nameQuery: String): Set<Long> =
        memberRepository.findIdsByNameContaining(nameQuery).toSet()
}
