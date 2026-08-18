package team.inreok.getiserver.domain.member.query

import org.springframework.modulith.NamedInterface

/**
 * `recommendation` Module의 Daily Scheduler(R4, Issue #160)가 일일 추천 생성 대상 회원을 결정할 때
 * 읽는 공개 계약이다. `recommendation`은 이 Interface를 통해서만 Member를 읽고, `Member` Entity나
 * `MemberRepository`/`MemberRoleRepository`를 직접 참조하지 않는다.
 *
 * "추천을 활성화했는가"(`RecommendationPreference.enabled`)는 `recommendation` 자신의 Table이라
 * 여기서 다루지 않는다 -- `recommendation`이 이미 활성화한 회원 memberId 집합을 먼저 좁혀 온 뒤,
 * 그 집합 중 "지금도 실제로 추천 대상일 수 있는 재학생인가"만 이 Port에 되묻는 구조다(활성화한
 * 회원 수가 전체 회원 수보다 훨씬 적을 것으로 예상되는 현재 규모에서, 전체 Member를 순회하는 대신
 * 이미 좁혀진 집합만 IN 절로 필터링해 N+1 없이 한 Query로 끝낸다).
 */
@NamedInterface
interface RecommendationAudienceQueryPort {
    /**
     * [memberIds] 중 지금 시점에 일일 추천 생성 대상일 수 있는 memberId만 반환한다(순서 보장
     * 없음). 대상 조건: `MemberStatus.ACTIVE` + `RoleType.STUDENT` + `AcademicStatus.ENROLLED`
     * (졸업·자퇴 회원과 교사·개발자 계정은 제외, Notion 기능명세 "졸업생에게는 추천 설정을 제공하지
     * 않는다"와 동일한 근거).
     */
    fun filterEligibleStudentIds(memberIds: Collection<Long>): List<Long>
}
