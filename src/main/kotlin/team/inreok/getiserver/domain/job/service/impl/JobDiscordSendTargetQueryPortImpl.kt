package team.inreok.getiserver.domain.job.service.impl

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.job.query.JobDiscordSendTargetQueryPort
import team.inreok.getiserver.domain.job.query.JobDiscordSendTargetSnapshot
import team.inreok.getiserver.domain.job.repository.JobRepository
import java.time.LocalDateTime

@Service
class JobDiscordSendTargetQueryPortImpl(
    private val jobRepository: JobRepository,
) : JobDiscordSendTargetQueryPort {
    @Transactional(readOnly = true)
    override fun countPublished(
        targetName: String?,
        targetGrade: Int?,
    ): Long = jobRepository.countPublishedDiscordSendTargets(targetName, targetGrade)

    @Transactional(readOnly = true)
    override fun findPublished(
        targetName: String?,
        targetGrade: Int?,
        afterCreatedAt: LocalDateTime?,
        afterId: Long?,
        limit: Int,
    ): List<JobDiscordSendTargetSnapshot> =
        jobRepository
            .findPublishedDiscordSendTargets(
                targetName,
                targetGrade,
                afterCreatedAt ?: FIRST_CURSOR_CREATED_AT,
                afterId ?: Long.MAX_VALUE,
                PageRequest.of(0, limit),
            ).map {
                JobDiscordSendTargetSnapshot(
                    jobId = requireNotNull(it.id) { "저장된 Job은 id를 가져야 합니다." },
                    title = it.title,
                    targetGrades = it.targetGrade?.let(::listOf),
                    createdAt = requireNotNull(it.createdAt) { "저장된 Job은 createdAt을 가져야 합니다." },
                )
            }

    private companion object {
        val FIRST_CURSOR_CREATED_AT = LocalDateTime.of(9999, 12, 31, 23, 59, 59)
    }
}
