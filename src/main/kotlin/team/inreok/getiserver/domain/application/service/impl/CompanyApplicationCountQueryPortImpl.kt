package team.inreok.getiserver.domain.application.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.application.repository.JobApplicationRepository
import team.inreok.getiserver.domain.company.query.CompanyApplicationCountQueryPort

@Service
class CompanyApplicationCountQueryPortImpl(
    private val jobApplicationRepository: JobApplicationRepository,
) : CompanyApplicationCountQueryPort {
    @Transactional(readOnly = true)
    override fun countByJobIds(jobIds: Set<Long>): Map<Long, Long> {
        if (jobIds.isEmpty()) return emptyMap()
        return jobApplicationRepository.countByJobIds(jobIds).associate { it.jobId to it.applicationCount }
    }
}
