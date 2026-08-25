package team.inreok.getiserver.domain.job.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.job.query.JobApplicationAdminFilterQueryPort
import team.inreok.getiserver.domain.job.repository.JobRepository

/**
 * `application` Module에 공개된 조회 계약([JobApplicationAdminFilterQueryPort])의 구현이다.
 * `JobApplicationSnapshotQueryPortImpl`과 같은 이유로 `JobServiceImpl`에 합치지 않고 분리한다.
 */
@Service
class JobApplicationAdminFilterQueryPortImpl(
    private val jobRepository: JobRepository,
) : JobApplicationAdminFilterQueryPort {
    @Transactional(readOnly = true)
    override fun findIdsByFilters(
        companyId: Long?,
        managerMemberId: Long?,
        mineOnlyMemberId: Long?,
    ): Set<Long> = jobRepository.findIdsByCompanyOrManagerFilter(companyId, managerMemberId, mineOnlyMemberId).toSet()
}
