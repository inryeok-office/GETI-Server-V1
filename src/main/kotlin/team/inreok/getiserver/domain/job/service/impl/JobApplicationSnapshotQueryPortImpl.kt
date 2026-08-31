package team.inreok.getiserver.domain.job.service.impl

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.job.entity.Job
import team.inreok.getiserver.domain.job.query.JobApplicationJobSnapshot
import team.inreok.getiserver.domain.job.query.JobApplicationSnapshotQueryPort
import team.inreok.getiserver.domain.job.repository.JobRepository

/**
 * `application` Module에 공개된 조회 계약([JobApplicationSnapshotQueryPort])의 구현이다.
 * `JobIndexQueryPortImpl`과 같은 이유로 `JobServiceImpl`에 합치지 않고 분리한다.
 */
@Service
class JobApplicationSnapshotQueryPortImpl(
    private val jobRepository: JobRepository,
) : JobApplicationSnapshotQueryPort {
    @Transactional(readOnly = true)
    override fun findById(jobId: Long): JobApplicationJobSnapshot? {
        val job = jobRepository.findByIdAndDeletedAtIsNull(jobId) ?: return null
        return toSnapshot(job)
    }

    @Transactional(readOnly = true)
    override fun findAllByIds(jobIds: Set<Long>): Map<Long, JobApplicationJobSnapshot> {
        if (jobIds.isEmpty()) return emptyMap()
        return jobRepository
            .findAllByIdInAndDeletedAtIsNull(jobIds)
            .associate { requireNotNull(it.id) { "저장된 Job은 id를 가져야 합니다." } to toSnapshot(it) }
    }

    @Transactional(readOnly = true)
    override fun findManagedByMemberId(
        memberId: Long,
        pageable: Pageable,
    ): Page<JobApplicationJobSnapshot> = jobRepository.findManagedByMemberId(memberId, pageable).map(::toSnapshot)

    private fun toSnapshot(job: Job): JobApplicationJobSnapshot =
        JobApplicationJobSnapshot(
            jobId = requireNotNull(job.id) { "저장된 Job은 id를 가져야 합니다." },
            title = job.title,
            companyId = job.companyId,
            postingType = job.type.name,
            applicationMethod = job.applicationMethod.name,
            status = job.status.name,
            targetGrade = job.targetGrade,
            recruitmentStartedAt = job.recruitmentStartedAt,
            recruitmentEndedAt = job.recruitmentEndedAt,
            createdByMemberId = job.createdByMemberId,
            managerMemberId = job.managerMemberId,
            viewCount = job.viewCount,
        )
}
