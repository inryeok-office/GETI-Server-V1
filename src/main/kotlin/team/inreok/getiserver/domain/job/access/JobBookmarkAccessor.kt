package team.inreok.getiserver.domain.job.access

import org.springframework.modulith.NamedInterface

/**
 * `job`이 소유하고 `recommendation`이 구현하는 SPI다(`JobApplicationEligibilityAccessor`와 같은
 * 방식 -- `docs/architecture/modularity.md` "Domain 간 허용 의존" 참고). 요청자 기준 북마크
 * 여부를 Job 상세·검색 응답에 포함하기 위해 필요하다(Issue #171).
 *
 * 방향을 이렇게 둔 이유: `recommendation`은 이미 `job.query.JobRecommendationCandidateQueryPort`/
 * `job.access.JobApplicationEligibilityAccessor`로 `job`에 의존한다. 만약 `job`이 거꾸로
 * `recommendation.query`에 의존하면 `recommendation -> job -> recommendation` 순환 의존이 생겨
 * `ModularityTest`(`modules.verify()`)가 실패한다. 대신 `job`이 이 Interface(자신이 소유한 타입)를
 * 정의하고, `recommendation`이 구현체를 Bean으로 등록한다 -- `job`은 `recommendation`의 어떤
 * Package도 참조하지 않는다.
 *
 * `search`(`JobSearchServiceImpl`)도 같은 Interface를 그대로 소비한다 -- `search`는 이미 공고
 * 색인·동기화 때문에 `job`에 의존하므로(Issue #69) 새 순환을 만들지 않는다
 * (`JobApplicationEligibilityAccessor`와 같은 이유로 여러 소비자가 같은 SPI를 공유한다).
 *
 * 결과를 `Map<Long, JobApplicationEligibilityAccessSnapshot>`처럼 풍부한 Snapshot Type이 아니라
 * `Set<Long>`(북마크한 jobId만)으로 두는 이유: 북마크는 값 하나(Boolean)뿐이라 Snapshot Type을
 * 새로 만들 필요가 없고, 이미 있는
 * `MemberJobPreferenceRepository.findBookmarkedJobIdsByMemberIdAndJobIdIn`이 정확히 이 모양
 * (jobId List)을 반환한다.
 */
@NamedInterface
interface JobBookmarkAccessor {
    /**
     * [jobIds] 중 [requesterMemberId]가 북마크한 jobId만 돌려준다(N+1 방지, `Set` 하나로 여러
     * Job의 북마크 여부를 한 번에 조회한다). 결과 Set에 없는 jobId는 북마크하지 않은 것으로
     * 취급한다(Preference Row 자체가 없거나, `exclusion`만 있고 `bookmarked=false`인 경우
     * 모두 포함) -- 즉 존재하지 않는 jobId를 넘겨도 예외 없이 그냥 결과에서 빠진다.
     */
    fun findAllByJobIds(
        jobIds: Set<Long>,
        requesterMemberId: Long,
    ): Set<Long>
}
