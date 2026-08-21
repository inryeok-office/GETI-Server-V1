package team.inreok.getiserver.domain.job.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.company.query.CompanyAdminJobQueryPort
import team.inreok.getiserver.domain.company.query.CompanyAdminJobSnapshot
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.repository.JobRepository

@Service
class CompanyAdminJobQueryPortImpl(
    private val jobRepository: JobRepository,
) : CompanyAdminJobQueryPort {
    @Transactional(readOnly = true)
    override fun findByCompanyId(companyId: Long): List<CompanyAdminJobSnapshot> =
        jobRepository
            .findAllByCompanyIdAndStatusInAndDeletedAtIsNull(
                companyId,
                listOf(JobStatus.DRAFT, JobStatus.PUBLISHED, JobStatus.CLOSED),
            ).map { job ->
                CompanyAdminJobSnapshot(
                    jobId = requireNotNull(job.id),
                    title = job.title,
                    postingType = job.type.name,
                    status = job.status.name,
                    recruitmentEndedAt = job.recruitmentEndedAt,
                )
            }
}
