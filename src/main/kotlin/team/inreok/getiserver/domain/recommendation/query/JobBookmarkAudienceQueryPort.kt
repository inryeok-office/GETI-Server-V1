package team.inreok.getiserver.domain.recommendation.query

import org.springframework.modulith.NamedInterface

/**
 * `notification` Module이 `JOB_CLOSED` 알림(Issue #191)의 수신자를 결정할 때 읽는 공개 계약이다.
 * `notification`은 이 Interface를 통해서만 `MemberJobPreference`를 읽고, 그 Entity나
 * `MemberJobPreferenceRepository`를 직접 참조하지 않는다.
 *
 * Issue #191 확정 정책: 마감 공고는 대상 학년 전체가 아니라 그 공고에 실제 관심 관계(북마크)가
 * 있는 회원에게만 알린다. `job.access.JobBookmarkAccessor`(요청자 1명 기준 여러 Job의 북마크
 * 여부)와 반대 방향 조회라 재사용하지 않는다 -- 여기서는 Job 1건 기준 여러 회원(북마크한 사람
 * 전체)이 필요하다.
 *
 * `notification`이 이 Port로 `recommendation`에 의존해도 `recommendation`은 `notification`의
 * 어떤 Package도 참조하지 않아 순환 의존이 생기지 않는다(`job.access`의 여러 SPI처럼 반대 방향
 * 역참조가 필요한 상황이 아니라, `recommendation`이 소유한 데이터를 그대로 공개하는 일반적인
 * `query` 방향이다).
 */
@NamedInterface
interface JobBookmarkAudienceQueryPort {
    /** [jobId]를 북마크한 회원 id 목록이다. 북마크한 회원이 없으면 빈 목록을 반환한다. */
    fun findBookmarkedMemberIds(jobId: Long): List<Long>
}
