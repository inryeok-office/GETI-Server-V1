package team.inreok.getiserver.domain.member.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.member.query.ApplicationApplicantProfile
import team.inreok.getiserver.domain.member.query.ApplicationApplicantProfileQueryPort
import team.inreok.getiserver.domain.member.repository.MemberRepository

/** `application` Module에 공개된 조회 계약([ApplicationApplicantProfileQueryPort])의 구현이다. */
@Service
class ApplicationApplicantProfileQueryPortImpl(
    private val memberRepository: MemberRepository,
) : ApplicationApplicantProfileQueryPort {
    @Transactional(readOnly = true)
    override fun findAllByIds(memberIds: Set<Long>): Map<Long, ApplicationApplicantProfile> {
        if (memberIds.isEmpty()) return emptyMap()
        return memberRepository
            .findAllById(memberIds)
            .associateBy(
                keySelector = { requireNotNull(it.id) { "저장된 Member는 id를 가져야 합니다." } },
                valueTransform = { member ->
                    ApplicationApplicantProfile(
                        memberId = requireNotNull(member.id) { "저장된 Member는 id를 가져야 합니다." },
                        name = member.name,
                        profileImageFileId = member.profileImageFileId,
                        cohort = member.cohort,
                        department = member.department?.name,
                    )
                },
            )
    }
}
