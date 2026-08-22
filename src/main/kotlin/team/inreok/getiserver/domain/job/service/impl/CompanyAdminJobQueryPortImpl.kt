package team.inreok.getiserver.domain.job.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.company.query.CompanyAdminJobQueryPort
import team.inreok.getiserver.domain.company.query.CompanyAdminJobSnapshot
import team.inreok.getiserver.domain.job.entity.Job
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.repository.JobRepository
import java.time.LocalDateTime

@Service
class CompanyAdminJobQueryPortImpl(
    private val jobRepository: JobRepository,
) : CompanyAdminJobQueryPort {
    @Transactional(readOnly = true)
    override fun findByCompanyId(companyId: Long): List<CompanyAdminJobSnapshot> =
        jobRepository
            .findAllByCompanyIdAndStatusInAndDeletedAtIsNull(
                companyId,
                VISIBLE_ADMIN_STATUSES,
            ).map(::toSnapshot)

    @Transactional(readOnly = true)
    override fun findByCompanyIds(companyIds: Set<Long>): List<CompanyAdminJobSnapshot> {
        if (companyIds.isEmpty()) return emptyList()
        return jobRepository
            .findAllByCompanyIdInAndStatusInAndDeletedAtIsNull(companyIds, VISIBLE_ADMIN_STATUSES)
            .map(::toSnapshot)
    }

    @Transactional(readOnly = true)
    override fun hasActiveJob(
        companyId: Long,
        now: LocalDateTime,
    ): Boolean = jobRepository.existsActiveByCompanyId(companyId, now)

    private fun toSnapshot(job: Job): CompanyAdminJobSnapshot =
        CompanyAdminJobSnapshot(
            companyId = job.companyId,
            jobId = requireNotNull(job.id),
            title = job.title,
            postingType = job.type.name,
            applicationMethod = job.applicationMethod.name,
            status = job.status.name,
            recruitmentStartedAt = job.recruitmentStartedAt,
            recruitmentEndedAt = job.recruitmentEndedAt,
            location = job.location,
            employmentType = job.employmentType,
            sourceName = job.sourceName,
        )

    private companion object {
        val VISIBLE_ADMIN_STATUSES = listOf(JobStatus.DRAFT, JobStatus.PUBLISHED, JobStatus.CLOSED)
    }
}
