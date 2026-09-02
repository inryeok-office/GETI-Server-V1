package team.inreok.getiserver.domain.job.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.job.entity.Job
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.query.JobRecommendationCandidateQueryPort
import team.inreok.getiserver.domain.job.query.JobRecommendationCandidateSnapshot
import team.inreok.getiserver.domain.job.repository.JobRepository

/** [JobRecommendationCandidateQueryPort]의 구현이다(Recommendation R2, Issue #148). */
@Service
class JobRecommendationCandidateQueryPortImpl(
    private val jobRepository: JobRepository,
) : JobRecommendationCandidateQueryPort {
    @Transactional(readOnly = true)
    override fun findAllPublished(): List<JobRecommendationCandidateSnapshot> =
        jobRepository
            .findAllByStatusAndDeletedAtIsNull(JobStatus.PUBLISHED)
            .map { it.toSnapshot() }

    @Transactional(readOnly = true)
    override fun findAllByIds(jobIds: Set<Long>): Map<Long, JobRecommendationCandidateSnapshot> {
        if (jobIds.isEmpty()) return emptyMap()
        return jobRepository
            .findAllByIdInAndDeletedAtIsNull(jobIds)
            .associate { requireNotNull(it.id) { "저장된 Job은 id를 가져야 합니다." } to it.toSnapshot() }
    }

    private fun Job.toSnapshot() =
        JobRecommendationCandidateSnapshot(
            jobId = requireNotNull(id) { "저장된 Job은 id를 가져야 합니다." },
            companyId = companyId,
            title = title,
            status = status.name,
            targetGrade = targetGrade,
            publishedAt = publishedAt,
            recruitmentEndedAt = recruitmentEndedAt,
            postingType = type,
            applicationMethod = applicationMethod,
            viewCount = viewCount,
            location = location,
            employmentType = employmentType,
        )
}
