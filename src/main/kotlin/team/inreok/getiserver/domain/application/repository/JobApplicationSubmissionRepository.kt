package team.inreok.getiserver.domain.application.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.inreok.getiserver.domain.application.entity.JobApplicationSubmission

interface JobApplicationSubmissionRepository : JpaRepository<JobApplicationSubmission, Long> {
    // 다음 submissionNumber를 정할 때 사용한다(Issue #133, JobApplicationRepository의
    // findTopByJobIdAndApplicantMemberIdOrderByAttemptNumberDesc와 동일한 관례). 이 지원서가
    // 이전에 제출된 적이 없으면 null.
    fun findTopByApplicationIdOrderBySubmissionNumberDesc(applicationId: Long): JobApplicationSubmission?

    /**
     * [findTopByApplicationIdOrderBySubmissionNumberDesc]의 배치 버전이다(Issue #138, PR #157
     * 코드리뷰 반영). 지원서마다 단건 조회를 반복하면 지원자 수만큼 Query가 느는 N+1이 되므로,
     * `applicationId`별 최신(submissionNumber가 가장 큰) 제출 Snapshot 1건씩을 한 Query로
     * 가져온다. 상관 서브쿼리로 "그룹별 최댓값 Row"를 고른다 -- PostgreSQL/H2 모두 지원하는
     * 표준 JPQL 패턴이라 `DISTINCT ON` 같은 PostgreSQL 전용 문법에 기대지 않는다.
     */
    @Query(
        """
        SELECT s FROM JobApplicationSubmission s
        WHERE s.applicationId IN :applicationIds
          AND s.submissionNumber = (
              SELECT MAX(s2.submissionNumber) FROM JobApplicationSubmission s2
              WHERE s2.applicationId = s.applicationId
          )
        """,
    )
    fun findLatestByApplicationIdIn(
        @Param("applicationIds") applicationIds: Collection<Long>,
    ): List<JobApplicationSubmission>
}
