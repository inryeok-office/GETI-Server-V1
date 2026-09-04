package team.inreok.getiserver.domain.member.query

import org.springframework.modulith.NamedInterface

/** Job 관리 화면에서 공고 담당자를 표시하기 위한 최소 회원 조회 계약이다. */
@NamedInterface
interface JobManagerSnapshotQueryPort {
    /** 존재하는 회원만 반환하며, 목록 응답의 행별 조회를 피하기 위해 id 집합을 한 번에 조회한다. */
    fun findAllByIds(memberIds: Set<Long>): Map<Long, JobManagerSnapshot>
}

@NamedInterface
data class JobManagerSnapshot(
    val memberId: Long,
    val name: String?,
)
