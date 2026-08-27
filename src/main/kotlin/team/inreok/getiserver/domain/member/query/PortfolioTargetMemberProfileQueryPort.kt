package team.inreok.getiserver.domain.member.query

import org.springframework.modulith.NamedInterface

/**
 * `portfolio` Module이 관리자 제출 현황(요구사항 §19, Issue #204 Phase 2b)에 실을 대상 학생 프로필을
 * 읽는 공개 계약이다. 대상 여부 검증만 하는 [PortfolioTargetMemberQueryPort]와 달리 이 계약은
 * 이름·기수·학과 같은 표시용 Snapshot을 돌려준다 -- 그 Port의 KDoc이 예고한 "학생 프로필이 필요한
 * 제출 현황 응답은 이후 Phase에서 Snapshot 계약을 추가한다"에 해당한다.
 *
 * `portfolio`는 이 Interface를 통해서만 Member를 읽고 `Member` Entity나 `MemberRepository`를 직접
 * 참조하지 않는다([MemberApplicantSnapshotQueryPort]/[InquiryMemberSnapshotQueryPort]와 같은 방식).
 *
 * 학번(studentNumber)은 포함하지 않는다 -- `members` 실제 스키마에 해당 Column이 없다
 * ([MemberApplicantSnapshot]과 동일한 판단).
 */
@NamedInterface
interface PortfolioTargetMemberProfileQueryPort {
    /**
     * [memberIds]에 해당하는 회원 프로필을 배치로 조회해 `memberId -> Snapshot` Map으로 돌려준다
     * (대상 수만큼 단건 조회를 반복하지 않기 위한 N+1 방지, §35). 상태(ACTIVE 등)나 Role로 거르지
     * 않는다 -- 이미 확정된 제출 대상의 표시용 정보라, 대상이 된 뒤 탈퇴·정지한 학생도 현황에서
     * 빠지지 않고 그대로 보여야 하기 때문이다. 존재하지 않는 id는 결과 Map에서 빠진다.
     */
    fun findProfiles(memberIds: Set<Long>): Map<Long, PortfolioTargetMemberProfile>
}

@NamedInterface
data class PortfolioTargetMemberProfile(
    val memberId: Long,
    val name: String?,
    val cohort: Int?,
    /** `DepartmentType.name` */
    val department: String?,
)
