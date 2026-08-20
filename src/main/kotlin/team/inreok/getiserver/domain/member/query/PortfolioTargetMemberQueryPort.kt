package team.inreok.getiserver.domain.member.query

import org.springframework.modulith.NamedInterface

/**
 * `portfolio` Module이 수합 요청의 제출 대상 학생을 검증할 때 쓰는 공개 계약이다(Portfolio 도메인
 * 요구사항 §21/§26). `portfolio`는 이 Interface를 통해서만 Member를 읽고 `Member` Entity나
 * `MemberRepository`를 직접 참조하지 않는다([MemberApplicantSnapshotQueryPort]/
 * [InquiryMemberSnapshotQueryPort]와 같은 방식).
 *
 * 대상 학생 프로필(name/cohort/department 등)은 이 계약에 포함하지 않는다 -- Phase 1(Core)의
 * 목록·상세 응답은 개수(targetCount/submittedCount)만 필요하고, 학생 프로필이 필요한 제출 현황
 * 응답은 이후 Phase에서 [InquiryMemberSnapshotQueryPort] 같은 Snapshot 계약을 재사용/추가한다.
 */
@NamedInterface
interface PortfolioTargetMemberQueryPort {
    /**
     * [memberIds] 중 실제로 존재하고 ACTIVE 상태이며 STUDENT 역할을 가진 회원의 id만 돌려준다(N+1
     * 방지를 위해 배치로 조회한다). 존재하지 않거나 ACTIVE/STUDENT가 아닌 id는 결과에서 빠지므로,
     * 호출 측은 요청한 집합과 결과 집합의 차이로 "잘못된 대상"을 판정할 수 있다.
     *
     * ACTIVE만 대상으로 삼는 이유: 탈퇴·정지·승인 대기(로그인 불가) 회원을 대상에 넣으면 제출할 수
     * 없는 회원이 `targetCount`에 남아 제출/미제출 현황(§19)이 영구히 어긋난다(`MemberRepository`의
     * 다른 회원 조회 관례와 동일).
     *
     * 재학/졸업 여부(academicStatus)는 걸러내지 않는다 -- 졸업생을 Target으로 허용할지는 확정되지
     * 않은 Product Decision이라(§39-8) 여기서 임의로 배제하지 않는다.
     */
    fun findStudentMemberIds(memberIds: Set<Long>): Set<Long>
}
