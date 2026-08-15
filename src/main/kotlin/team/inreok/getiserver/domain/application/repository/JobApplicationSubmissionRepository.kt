package team.inreok.getiserver.domain.application.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.application.entity.JobApplicationSubmission

interface JobApplicationSubmissionRepository : JpaRepository<JobApplicationSubmission, Long> {
    // 다음 submissionNumber를 정할 때 사용한다(Issue #133, JobApplicationRepository의
    // findTopByJobIdAndApplicantMemberIdOrderByAttemptNumberDesc와 동일한 관례). 이 지원서가
    // 이전에 제출된 적이 없으면 null.
    fun findTopByApplicationIdOrderBySubmissionNumberDesc(applicationId: Long): JobApplicationSubmission?
}
