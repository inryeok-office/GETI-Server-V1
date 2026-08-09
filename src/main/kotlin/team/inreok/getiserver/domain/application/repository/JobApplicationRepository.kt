package team.inreok.getiserver.domain.application.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.application.entity.JobApplication
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus

interface JobApplicationRepository : JpaRepository<JobApplication, Long> {
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
}
