package team.inreok.getiserver.domain.company.query

import org.springframework.modulith.NamedInterface

@NamedInterface
interface CompanyApplicationCountQueryPort {
    fun countByJobIds(jobIds: Set<Long>): Map<Long, Long>
}
