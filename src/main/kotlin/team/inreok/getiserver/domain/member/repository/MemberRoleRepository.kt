package team.inreok.getiserver.domain.member.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.member.entity.MemberRole
import team.inreok.getiserver.domain.member.entity.MemberRoleId

interface MemberRoleRepository : JpaRepository<MemberRole, MemberRoleId> {
    fun findAllByIdMemberId(memberId: Long): List<MemberRole>

    // 관리자 회원 목록(Issue #183)의 항목마다 보유 Role을 표시할 때 쓴다. 항목 수만큼 단건 조회를
    // 반복하면 N+1이 되므로 Page에 등장하는 memberId 전체를 한 Query로 배치 조회한다
    // (JobApplicationAdminServiceImpl의 배치 조회와 동일한 원칙).
    fun findAllByIdMemberIdIn(memberIds: Collection<Long>): List<MemberRole>

    // Role Set 전체 교체(Issue #183 DECISION_REQUIRED 확정)의 첫 단계다. 기존 Role을 모두 지운 뒤
    // 요청받은 집합을 다시 저장한다 -- 개별 추가/제거 diff보다 단순하고, 같은 Transaction 안에서
    // 이뤄져 부분 성공(일부만 지워지고 일부만 저장됨)이 남지 않는다.
    fun deleteAllByIdMemberId(memberId: Long)
}
