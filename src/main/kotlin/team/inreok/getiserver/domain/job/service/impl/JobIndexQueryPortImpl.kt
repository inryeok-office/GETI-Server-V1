package team.inreok.getiserver.domain.job.service.impl

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.job.entity.Job
import team.inreok.getiserver.domain.job.query.JobIndexQueryPort
import team.inreok.getiserver.domain.job.query.JobIndexSnapshot
import team.inreok.getiserver.domain.job.repository.JobRepository
import team.inreok.getiserver.domain.job.service.PUBLIC_VISIBLE_STATUSES

/**
 * 다른 Domain Module(search)에 공개된 조회 계약([JobIndexQueryPort])의 구현이다. `CompanyQueryImpl`과
 * 같은 이유로 `JobServiceImpl`에 합치지 않고 분리한다(Issue #69).
 */
@Service
class JobIndexQueryPortImpl(
    private val jobRepository: JobRepository,
) : JobIndexQueryPort {
    // 존재하지 않음/비공개 상태 각각을 조기 반환하는 편이 중첩 if/else보다 읽기 쉽다고 판단해
    // Suppress한다(CollectorExecutionServiceImpl.collectForScheduledSource와 같은 이유).
    @Suppress("ReturnCount")
    @Transactional(readOnly = true)
    override fun findById(jobId: Long): JobIndexSnapshot? {
        val job = jobRepository.findByIdAndDeletedAtIsNull(jobId) ?: return null
        if (job.status !in PUBLIC_VISIBLE_STATUSES) return null
        return toSnapshot(job)
    }

    @Transactional(readOnly = true)
    override fun findForReindex(
        afterId: Long,
        limit: Int,
    ): List<JobIndexSnapshot> =
        jobRepository
            .findForReindex(PUBLIC_VISIBLE_STATUSES, afterId, PageRequest.of(0, limit))
            .content
            .map(::toSnapshot)

    private fun toSnapshot(job: Job) =
        JobIndexSnapshot(
            jobId = requireNotNull(job.id) { "저장된 Job은 id를 가져야 합니다." },
            title = job.title,
            content = job.bodyMarkdown,
            postingType = job.type.name,
            applicationMethod = job.applicationMethod.name,
            status = job.status.name,
            companyId = job.companyId,
            targetGrade = job.targetGrade,
            capacity = job.capacity,
            firstComeServed = job.firstComeServed,
            viewCount = job.viewCount,
            publishedAt = job.publishedAt,
            startDate = job.recruitmentStartedAt,
            endDate = job.recruitmentEndedAt,
            sourceName = job.sourceName,
            location = job.location,
            employmentType = job.employmentType,
        )
}
