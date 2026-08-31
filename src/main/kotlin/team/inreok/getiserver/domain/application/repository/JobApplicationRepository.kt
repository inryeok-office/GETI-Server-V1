package team.inreok.getiserver.domain.application.repository

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.inreok.getiserver.domain.application.entity.JobApplication
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus
import java.time.LocalDateTime

interface JobApplicationRepository : JpaRepository<JobApplication, Long> {
    @Query(
        """
        SELECT a.jobId AS jobId,
               COUNT(a.id) AS applicantCount,
               SUM(CASE WHEN a.status IN (
                   team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus.SUBMITTED,
                   team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus.EDIT_REQUESTED
               ) THEN 1 ELSE 0 END) AS pendingCount
        FROM JobApplication a
        WHERE a.jobId IN :jobIds
          AND a.status <> team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus.DRAFT
        GROUP BY a.jobId
        """,
    )
    fun summarizeByJobIds(
        @Param("jobIds") jobIds: Set<Long>,
    ): List<JobApplicationJobSummaryProjection>

    @Query(
        """
        SELECT a.jobId AS jobId, COUNT(a.id) AS applicationCount
        FROM JobApplication a
        WHERE a.jobId IN :jobIds
          AND a.status <> team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus.DRAFT
        GROUP BY a.jobId
        """,
    )
    fun countByJobIds(
        @Param("jobIds") jobIds: Set<Long>,
    ): List<JobApplicationCountProjection>

    @Query(
        """
        SELECT a.status AS status, COUNT(a.id) AS applicationCount
        FROM JobApplication a
        WHERE a.status <> team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus.DRAFT
        GROUP BY a.status
        """,
    )
    fun countByStatusForAdmin(): List<JobApplicationStatusCountProjection>

    /**
     * 학생 Action(SUBMIT/REQUEST_EDIT/RESUBMIT/WITHDRAW)이 같은 지원서를 동시에 전이시키려는
     * 경합을 막기 위해 Pessimistic Write Lock으로 조회한다(요구사항 "동시성/데이터 무결성" 절).
     * `ProgramRepository.findByIdForUpdate`/`InquiryRepository.findByIdForUpdate`와 같은 이유와
     * 방식이다 -- 상태를 확인·전이하는 모든 Action은 이 Method로 조회한 뒤 재확인해야 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM JobApplication a WHERE a.id = :id")
    fun findByIdForUpdate(
        @Param("id") id: Long,
    ): JobApplication?

    fun findByJobIdAndApplicantMemberIdAndAttemptNumber(
        jobId: Long,
        applicantMemberId: Long,
        attemptNumber: Int,
    ): JobApplication?

    // 다음 attemptNumber를 정할 때 사용한다(요구사항 22절, 취소 후 재지원은 새 attemptNumber로
    // 새 Row가 된다). 이 학생이 이 공고에 지원한 적이 없으면 null.
    fun findTopByJobIdAndApplicantMemberIdOrderByAttemptNumberDesc(
        jobId: Long,
        applicantMemberId: Long,
    ): JobApplication?

    // 학생 지원 가능 여부 판단(요구사항 7절)의 ALREADY_APPLIED 검사에 사용한다. 활성 상태
    // 집합은 Service가 결정한다(WITHDRAWN/REJECTED는 활성이 아니라 재지원 가능). 정상 상태에서는
    // (jobId, applicantMemberId)당 활성 Row가 최대 1건이지만(uk_job_applications_active_singleton,
    // V13 Migration), 동시 요청으로 그 불변식이 깨지는 경합 구간(PR #79 Review 반영)에서도
    // 단일 nullable 반환 Query는 IncorrectResultSizeDataAccessException으로 죽어 이후 조회까지
    // 계속 실패하므로 다건을 그대로 받을 수 있는 List로 반환한다.
    fun findByJobIdAndApplicantMemberIdAndStatusIn(
        jobId: Long,
        applicantMemberId: Long,
        statuses: Collection<JobApplicationStatus>,
    ): List<JobApplication>

    // findByJobIdAndApplicantMemberIdAndStatusIn의 배치 버전이다(Issue #136, Job/Search 응답에
    // 지원 가능 여부·지원 현황을 노출할 때 목록 한 Page(최대 100건)를 단건 조회 반복 없이 채우기
    // 위한 조회). 정상 상태에서는 (jobId, applicantMemberId)당 활성 Row가 최대 1건이지만
    // (uk_job_applications_active_singleton), 위 Method와 같은 이유로 List로 반환한다.
    fun findByJobIdInAndApplicantMemberIdAndStatusIn(
        jobIds: Collection<Long>,
        applicantMemberId: Long,
        statuses: Collection<JobApplicationStatus>,
    ): List<JobApplication>

    // 교사·개발자용 지원서 목록 조회다(Issue #125 요구사항 "모든 교사가 조회 가능", Issue #181로
    // applicantName/cohort/department/jobIds Filter 확장, Issue #203으로 applicationIds Filter
    // 추가). :jobId, :status는 null이면 조건을 적용하지 않는다(FormRepository.search와 동일한
    // 관례). Issue #125는 "제출된 지원서"만 대상으로 하므로 DRAFT(임시저장 중, 미제출)는 :status로
    // 명시 지정해도 항상 제외한다(PR #130 Review 반영, 담당 공고 아닌 교사에게 학생 임시저장이
    // 노출되는 문제 방지).
    //
    // :hasApplicantName이 false면 이 조건은 항상 참으로 취급된다(InquiryRepository.searchForAdmin
    // KDoc의 :hasQuery와 동일한 이유 -- :applicantName을 null로 바인딩하면 LOWER(CONCAT(...))
    // Type 추론 문제(PostgreSQL이 bytea로 잘못 확정)가 나므로 항상 실제 String 값을 바인딩하고
    // :hasApplicantName으로 조건 평가 여부만 가른다). :applicantName은 Service 계층에서 LIKE
    // Wildcard(%, _)를 미리 이스케이프해 전달한다(domain.application.service.escapeLikePattern).
    //
    // :department는 DepartmentType.name(String)이다 -- applicantDepartment Column 자체가 Enum이
    // 아닌 String Snapshot이라(JobApplication.applicantDepartment KDoc 참고) Service 계층에서
    // 변환해 전달한다.
    //
    // :hasJobFilter가 false면 :jobIds 조건은 적용하지 않는다. companyId/managerMemberId/mineOnly
    // Filter는 application이 job.query.JobApplicationAdminFilterQueryPort로 미리 조회한 Job id
    // 집합을 :jobIds로 전달한다(Module 경계 유지, Cross-Module Join 금지). 기존 :jobId(단일)
    // 조건과는 별개로 AND 결합되므로, 두 조건이 서로 다른 공고를 가리키면 결과는 정직하게 빈
    // 목록이 된다(InquiryRepository.searchForAdmin의 assigneeId/mineOnlyMemberId와 동일한 관례).
    //
    // :hasApplicationIds가 false면 :applicationIds 조건은 적용하지 않는다(:hasJobFilter/:jobIds와
    // 동일한 관례 -- 모든 신규 Filter는 In-Memory가 아닌 DB 수준(JPQL)에서 처리한다). :jobId와
    // AND로 결합되므로, 이 공고에 속하지 않거나 존재하지 않는 id는 결과에서 자연히 빠진다(Issue
    // #203 정책 확정: 오류로 처리하지 않고 조용히 무시).
    @Query(
        """
        SELECT a FROM JobApplication a
        WHERE a.status <> team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus.DRAFT
          AND (:jobId IS NULL OR a.jobId = :jobId)
          AND (:status IS NULL OR a.status = :status)
          AND (
            :hasApplicantName = FALSE
            OR LOWER(a.applicantName) LIKE LOWER(CONCAT('%', :applicantName, '%')) ESCAPE '\'
          )
          AND (:cohort IS NULL OR a.applicantCohort = :cohort)
          AND (:department IS NULL OR a.applicantDepartment = :department)
          AND (:hasJobFilter = FALSE OR a.jobId IN :jobIds)
          AND (:hasApplicationIds = FALSE OR a.id IN :applicationIds)
          AND a.createdAt >= COALESCE(:createdFrom, a.createdAt)
          AND a.createdAt < COALESCE(:createdTo, CURRENT_TIMESTAMP)
        ORDER BY a.id DESC
        """,
    )
    fun search(
        @Param("jobId") jobId: Long?,
        @Param("status") status: JobApplicationStatus?,
        @Param("hasApplicantName") hasApplicantName: Boolean,
        @Param("applicantName") applicantName: String,
        @Param("cohort") cohort: Int?,
        @Param("department") department: String?,
        @Param("hasJobFilter") hasJobFilter: Boolean,
        @Param("jobIds") jobIds: Collection<Long>,
        @Param("hasApplicationIds") hasApplicationIds: Boolean,
        @Param("applicationIds") applicationIds: Collection<Long>,
        @Param("createdFrom") createdFrom: LocalDateTime? = null,
        @Param("createdTo") createdTo: LocalDateTime? = null,
        pageable: Pageable,
    ): Page<JobApplication>

    // 학생 본인 지원 목록 조회다(Issue #184). :status는 null이면 조건을 적용하지 않는다(search()와
    // 동일한 관례). admin의 search()와 달리 DRAFT를 제외하지 않는다 -- 본인의 임시저장은 admin
    // 목록(다른 사람이 보는 목록)과 달리 "내 지원 목록"에서 본인에게 보여야 다시 이어서 작성할 수
    // 있다(요구사항 "DRAFT 포함 여부와 상태 필터 의미를 기존 Application 정책과 일치시켜 문서화한다").
    @Query(
        """
        SELECT a FROM JobApplication a
        WHERE a.applicantMemberId = :applicantMemberId
          AND (:status IS NULL OR a.status = :status)
        ORDER BY a.id DESC
        """,
    )
    fun searchForApplicant(
        @Param("applicantMemberId") applicantMemberId: Long,
        @Param("status") status: JobApplicationStatus?,
        pageable: Pageable,
    ): Page<JobApplication>

    // 공고별 공개 신청자 목록이다(Issue #137). "SUBMITTED 이후 전체"(DRAFT/WITHDRAWN 제외)만
    // 대상으로 한다(정책 확정, Issue #137 코멘트) -- 아직 제출하지 않은 임시저장과 철회한 지원은
    // "신청자"가 아니다. search()와 달리 jobId는 필수이고 status 필터는 받지 않는다(Phase 9는
    // 상태별 필터링 요구사항이 없다).
    @Query(
        """
        SELECT a FROM JobApplication a
        WHERE a.jobId = :jobId
          AND a.status NOT IN (
            team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus.DRAFT,
            team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus.WITHDRAWN
          )
        ORDER BY a.id DESC
        """,
    )
    fun findApplicantsOf(
        @Param("jobId") jobId: Long,
        pageable: Pageable,
    ): Page<JobApplication>
}

interface JobApplicationCountProjection {
    val jobId: Long
    val applicationCount: Long
}

interface JobApplicationJobSummaryProjection {
    val jobId: Long
    val applicantCount: Long
    val pendingCount: Long
}

interface JobApplicationStatusCountProjection {
    val status: JobApplicationStatus
    val applicationCount: Long
}
