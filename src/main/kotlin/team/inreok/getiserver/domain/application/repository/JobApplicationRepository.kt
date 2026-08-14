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

interface JobApplicationRepository : JpaRepository<JobApplication, Long> {
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

    // 교사·개발자용 지원서 목록 조회다(Issue #125 요구사항 "모든 교사가 조회 가능"). :jobId,
    // :status는 null이면 조건을 적용하지 않는다(FormRepository.search와 동일한 관례). Issue #125는
    // "제출된 지원서"만 대상으로 하므로 DRAFT(임시저장 중, 미제출)는 :status로 명시 지정해도 항상
    // 제외한다(PR #130 Review 반영, 담당 공고 아닌 교사에게 학생 임시저장이 노출되는 문제 방지).
    @Query(
        """
        SELECT a FROM JobApplication a
        WHERE a.status <> team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus.DRAFT
          AND (:jobId IS NULL OR a.jobId = :jobId)
          AND (:status IS NULL OR a.status = :status)
        ORDER BY a.id DESC
        """,
    )
    fun search(
        @Param("jobId") jobId: Long?,
        @Param("status") status: JobApplicationStatus?,
        pageable: Pageable,
    ): Page<JobApplication>
}
