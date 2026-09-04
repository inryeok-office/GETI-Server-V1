package team.inreok.getiserver.domain.member.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.member.query.JobManagerSnapshot
import team.inreok.getiserver.domain.member.query.JobManagerSnapshotQueryPort
import team.inreok.getiserver.domain.member.repository.MemberRepository

@Service
class JobManagerSnapshotQueryPortImpl(
    private val memberRepository: MemberRepository,
) : JobManagerSnapshotQueryPort {
    @Transactional(readOnly = true)
    override fun findAllByIds(memberIds: Set<Long>): Map<Long, JobManagerSnapshot> {
        if (memberIds.isEmpty()) return emptyMap()
        return memberRepository.findAllById(memberIds).associateBy(
            keySelector = { requireNotNull(it.id) { "영속화된 Member는 id를 가져야 합니다." } },
            valueTransform = { member ->
                JobManagerSnapshot(
                    memberId = requireNotNull(member.id) { "영속화된 Member는 id를 가져야 합니다." },
                    name = member.name,
                )
            },
        )
    }
}
