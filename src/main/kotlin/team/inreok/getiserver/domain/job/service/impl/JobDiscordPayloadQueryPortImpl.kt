package team.inreok.getiserver.domain.job.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.company.query.CompanyQuery
import team.inreok.getiserver.domain.job.query.JobDiscordPayloadQueryPort
import team.inreok.getiserver.domain.job.query.JobDiscordPayloadSnapshot
import team.inreok.getiserver.domain.job.repository.JobRepository

/**
 * 다른 Domain Module(notification)에 공개된 조회 계약([JobDiscordPayloadQueryPort])의 구현이다.
 * [JobIndexQueryPortImpl]·[JobNotificationTargetQueryPortImpl]과 같은 이유로 `JobServiceImpl`에
 * 합치지 않고 분리한다.
 */
@Service
class JobDiscordPayloadQueryPortImpl(
    private val jobRepository: JobRepository,
    private val companyQuery: CompanyQuery,
) : JobDiscordPayloadQueryPort {
    @Transactional(readOnly = true)
    override fun findById(jobId: Long): JobDiscordPayloadSnapshot? {
        // 삭제된 공고도 DELETE_NOTICE의 대상이므로 Soft Delete를 거르는
        // findByIdAndDeletedAtIsNull이 아니라 findById를 쓴다.
        val job = jobRepository.findById(jobId).orElse(null) ?: return null
        return JobDiscordPayloadSnapshot(
            jobId = requireNotNull(job.id) { "저장된 Job은 id를 가져야 합니다." },
            title = job.title,
            companyId = job.companyId,
            // 삭제된 기업이면 null이다. 기업명은 Bot Schema에서 선택 필드라 없으면 생략된다.
            companyName = companyQuery.findActiveSummary(job.companyId)?.name,
            recruitmentEndedAt = job.recruitmentEndedAt,
            discordChannelKey = job.discordChannelKey,
            targetGrade = job.targetGrade,
            updatedAt = requireNotNull(job.updatedAt) { "저장된 Job은 updatedAt을 가져야 합니다." },
        )
    }

    @Transactional(readOnly = true)
    override fun findDisplayNamesByIds(jobIds: Set<Long>): Map<Long, String> {
        if (jobIds.isEmpty()) return emptyMap()
        // findById와 같은 이유로 Soft Delete를 거르지 않는다(Port KDoc 참고).
        return jobRepository
            .findAllById(jobIds)
            .associate { requireNotNull(it.id) { "저장된 Job은 id를 가져야 합니다." } to it.title }
    }
}
