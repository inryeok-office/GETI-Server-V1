package team.inreok.getiserver.domain.member.query

import org.springframework.modulith.NamedInterface

/**
 * `notification` Module이 공고·프로그램 게시 알림(`JOB_PUBLISHED`/`PROGRAM_PUBLISHED`, Issue #191)의
 * 수신 대상을 결정할 때 읽는 공개 계약이다. `notification`은 이 Interface를 통해서만 Member를
 * 읽고, `Member` Entity나 `MemberRepository`를 직접 참조하지 않는다.
 *
 * 대상 조건은 `JobApplicationEligibility.computeEligibilityReason`/
 * `ProgramEligibility.computeProgramEligibilityReason`가 이미 확정한 "재학 상태 + 대상 학년"
 * 기준을 그대로 따른다(Issue #191 확정 정책 -- Recommendation 점수는 게시 알림 수신 조건이
 * 아니다). `RecommendationAudienceQueryPort`(ACTIVE + STUDENT + ENROLLED)와 같은 기준에
 * 학년 조건만 추가한 상위 호환 계약이지만, 그 Port는 이미 좁혀진 후보 memberId 집합만 필터링하는
 * 반면 이 Port는 후보 집합 없이 전체 재학생 중에서 바로 대상을 찾는다는 점이 달라 별도로 둔다.
 */
@NamedInterface
interface NotificationAudienceQueryPort {
    /**
     * `MemberStatus.ACTIVE` + `RoleType.STUDENT` + `AcademicStatus.ENROLLED`인 회원 중
     * [targetGrades] 조건을 만족하는 memberId 목록을 반환한다(순서 보장 없음). [targetGrades]가
     * 비어 있으면 학년 제한 없이 전체 재학생을 대상으로 한다(공고의 `targetGrade == null`,
     * 프로그램의 빈 `program_target_grades`와 동일한 의미 -- 전 학년 대상).
     */
    fun findEligibleStudentIds(targetGrades: Set<Int>): List<Long>
}
