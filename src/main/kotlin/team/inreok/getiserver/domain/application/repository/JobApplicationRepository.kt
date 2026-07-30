package team.inreok.getiserver.domain.application.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.application.entity.JobApplication

interface JobApplicationRepository : JpaRepository<JobApplication, Long> {
    fun findByJobIdAndApplicantMemberIdAndAttemptNumber(
        jobId: Long,
        applicantMemberId: Long,
        attemptNumber: Int,
    ): JobApplication?
}
