package team.inreok.getiserver.domain.member.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.member.query.InquiryMemberSnapshot
import team.inreok.getiserver.domain.member.query.InquiryMemberSnapshotQueryPort
import team.inreok.getiserver.domain.member.repository.MemberRepository

/** `inquiry` Module에 공개된 조회 계약([InquiryMemberSnapshotQueryPort])의 구현이다. */
@Service
class InquiryMemberSnapshotQueryPortImpl(
    private val memberRepository: MemberRepository,
) : InquiryMemberSnapshotQueryPort {
    @Transactional(readOnly = true)
    override fun findAllByIds(memberIds: Set<Long>): Map<Long, InquiryMemberSnapshot> {
        if (memberIds.isEmpty()) return emptyMap()
        return memberRepository
            .findAllById(memberIds)
            .associateBy(
                keySelector = { requireNotNull(it.id) { "저장된 Member는 id를 가져야 합니다." } },
                valueTransform = { member ->
                    InquiryMemberSnapshot(
                        memberId = requireNotNull(member.id) { "저장된 Member는 id를 가져야 합니다." },
                        name = member.name,
                        profileImageFileId = member.profileImageFileId,
                        cohort = member.cohort,
                        department = member.department?.name,
                        isPublic = member.profilePublic,
                    )
                },
            )
    }
}
