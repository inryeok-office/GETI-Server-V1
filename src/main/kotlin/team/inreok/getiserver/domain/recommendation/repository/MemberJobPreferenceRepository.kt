package team.inreok.getiserver.domain.recommendation.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.inreok.getiserver.domain.recommendation.entity.MemberJobPreference
import team.inreok.getiserver.domain.recommendation.entity.type.ExclusionType

interface MemberJobPreferenceRepository :
    JpaRepository<MemberJobPreference, Long>,
    MemberJobBookmarkQueryRepository {
    fun findByMemberIdAndJobId(
        memberId: Long,
        jobId: Long,
    ): MemberJobPreference?

    /** 관심 없음 해제(Notion `DELETE /api/v1/me/recommendation-exclusions/{exclusionId}`)에서
     * 본인 소유 Resource만 대상으로 하기 위한 조회다 -- 다른 회원의 exclusionId를 조회하면 없는
     * 것과 동일하게 취급한다(404 EXCLUSION_NOT_FOUND, Notion 계약에 403이 없어 존재 여부 자체를
     * 노출하지 않는다). */
    fun findByIdAndMemberId(
        id: Long,
        memberId: Long,
    ): MemberJobPreference?

    /** 관심 없음 등록(Notion `POST /api/v1/me/recommendation-exclusions`)의 409
     * EXCLUSION_ALREADY_EXISTS 판정에 쓴다 -- exclusionType과 무관하게 같은 Job에 이미 어떤
     * 관심 없음이든 설정돼 있으면 중복이다(한 Row에는 exclusion 값이 하나만 저장될 수 있다). */
    fun existsByMemberIdAndJobIdAndExclusionIsNotNull(
        memberId: Long,
        jobId: Long,
    ): Boolean

    // exclusion이 THIS_JOB이든 SIMILAR_JOBS이든 값이 있으면 "관심 없음" 처리된 공고다(R1/R2
    // 설계 -- 두 값 모두 해당 공고를 추천 후보에서 제외하는 Hard Filter 대상). Batch로 한 번에
    // 조회해 회원의 Candidate 수만큼 반복 조회하지 않는다(N+1 방지).
    @Query(
        """
        SELECT p.jobId FROM MemberJobPreference p
        WHERE p.memberId = :memberId AND p.exclusion IS NOT NULL
        """,
    )
    fun findExcludedJobIdsByMemberId(
        @Param("memberId") memberId: Long,
    ): List<Long>

    /** SIMILAR_JOBS로 등록된 기준(Source) Job만 조회한다(R6, Issue #167). THIS_JOB은 그 Job
     * 자체만 제외하면 되므로 유사 공고 확장 대상에서 제외한다 -- [findExcludedJobIdsByMemberId]와
     * 달리 exclusionType을 SIMILAR_JOBS로 좁힌다. */
    @Query(
        """
        SELECT p.jobId FROM MemberJobPreference p
        WHERE p.memberId = :memberId AND p.exclusion = :exclusionType
        """,
    )
    fun findJobIdsByMemberIdAndExclusionType(
        @Param("memberId") memberId: Long,
        @Param("exclusionType") exclusionType: ExclusionType,
    ): List<Long>

    /** 이미 북마크한 공고를 추천 후보에서 제외하기 위한 Batch 조회다(Notion 기능명세 "이미
     * 북마크한 공고는 추천 목록에서 제외", Issue #155). [findExcludedJobIdsByMemberId]와 같은
     * 이유로 N+1을 피한다. */
    @Query(
        """
        SELECT p.jobId FROM MemberJobPreference p
        WHERE p.memberId = :memberId AND p.bookmarked = true
        """,
    )
    fun findBookmarkedJobIdsByMemberId(
        @Param("memberId") memberId: Long,
    ): List<Long>

    /** 이미 조회한 추천 결과의 Job이 회원의 북마크 대상인지 표시하기 위한 Batch 조회다(Notion
     * Job Summary의 `bookmarked` Field, Issue #155). [findBookmarkedJobIdsByMemberId]와 로직은
     * 같지만 호출 문맥(Hard Filter vs 응답 조립)이 달라 Service 쪽 가독성을 위해 별도 이름을
     * 쓴다 -- 특정 jobIds Set으로 좁혀 후보 이외의 Row까지 읽지 않는다. */
    @Query(
        """
        SELECT p.jobId FROM MemberJobPreference p
        WHERE p.memberId = :memberId AND p.jobId IN :jobIds AND p.bookmarked = true
        """,
    )
    fun findBookmarkedJobIdsByMemberIdAndJobIdIn(
        @Param("memberId") memberId: Long,
        @Param("jobIds") jobIds: Set<Long>,
    ): List<Long>

    /** 관심 없음 목록 조회(Notion `GET /api/v1/me/recommendation-exclusions`)다. [exclusionType]이
     * null이면 THIS_JOB/SIMILAR_JOBS 전체를 대상으로 한다(`MemberRepository`의 nullable Filter
     * 관례와 동일). 최신 관심 없음(`exclusionCreatedAt` DESC)부터 보여준다. */
    @Query(
        """
        SELECT p FROM MemberJobPreference p
        WHERE p.memberId = :memberId
          AND p.exclusion IS NOT NULL
          AND (:exclusionType IS NULL OR p.exclusion = :exclusionType)
        ORDER BY p.exclusionCreatedAt DESC, p.id DESC
        """,
    )
    fun findAllExclusionsByMemberId(
        @Param("memberId") memberId: Long,
        @Param("exclusionType") exclusionType: ExclusionType?,
        pageable: Pageable,
    ): Page<MemberJobPreference>

    /** Job Summary의 `bookmarkCount` Field(Issue #196)를 위한 Batch 집계다. Job별로 개별 COUNT
     * Query를 날리면 목록 항목 수만큼 Query가 늘어나(N+1) `jobIds`를 한 번에 묶어 GROUP BY로
     * 가져온다(`ProgramApplicationRepository.countActiveApplicantsByProgramIds`와 같은 관례).
     * 북마크가 하나도 없는 jobId는 결과에 없으므로 호출 측이 0으로 취급해야 한다. */
    @Query(
        """
        SELECT p.jobId AS jobId, COUNT(p) AS bookmarkCount FROM MemberJobPreference p
        WHERE p.jobId IN :jobIds AND p.bookmarked = true
        GROUP BY p.jobId
        """,
    )
    fun countBookmarksByJobIds(
        @Param("jobIds") jobIds: Collection<Long>,
    ): List<JobBookmarkCount>
}

// countBookmarksByJobIds 전용 Interface Projection이다(ProgramApplicantCount와 같은 관례). Kotlin
// Property는 Getter(getJobId/getBookmarkCount)로 컴파일되어 JPQL Alias와 이름으로 매칭된다.
interface JobBookmarkCount {
    val jobId: Long
    val bookmarkCount: Long
}
