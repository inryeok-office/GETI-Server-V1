package team.inreok.getiserver.domain.job.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.job.query.JobNotificationTargetQueryPort
import team.inreok.getiserver.domain.job.query.JobNotificationTargetSnapshot
import team.inreok.getiserver.domain.job.repository.JobRepository

/**
 * 다른 Domain Module(notification)에 공개된 조회 계약([JobNotificationTargetQueryPort])의
 * 구현이다. [JobIndexQueryPortImpl]과 같은 이유로 `JobServiceImpl`에 합치지 않고 분리한다.
 */
@Service
class JobNotificationTargetQueryPortImpl(
    private val jobRepository: JobRepository,
) : JobNotificationTargetQueryPort {
    @Transactional(readOnly = true)
    override fun findAllByIds(jobIds: Set<Long>): Map<Long, JobNotificationTargetSnapshot> {
        if (jobIds.isEmpty()) return emptyMap()
        // 삭제된 공고도 "삭제됨"으로 판정해야 하므로 findByIdAndDeletedAtIsNull이 아니라
        // Soft Delete를 거르지 않는 findAllById를 쓴다.
        return jobRepository
            .findAllById(jobIds)
            .mapNotNull { job ->
                val jobId = job.id ?: return@mapNotNull null
                jobId to
                    JobNotificationTargetSnapshot(
                        jobId = jobId,
                        status = job.status.name,
                        deleted = job.deletedAt != null,
                    )
            }.toMap()
    }
}
