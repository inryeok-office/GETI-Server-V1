package team.inreok.getiserver.domain.company.query

import org.springframework.modulith.NamedInterface
import java.time.LocalDateTime

@NamedInterface
interface CompanyAdminJobQueryPort {
    fun findByCompanyId(companyId: Long): List<CompanyAdminJobSnapshot>

    fun findByCompanyIds(companyIds: Set<Long>): List<CompanyAdminJobSnapshot>

    fun hasActiveJob(
        companyId: Long,
        now: LocalDateTime,
    ): Boolean
}

@NamedInterface
data class CompanyAdminJobSnapshot(
    val companyId: Long,
    val jobId: Long,
    val title: String,
    val postingType: String,
    val applicationMethod: String,
    val status: String,
    val recruitmentStartedAt: LocalDateTime?,
    val recruitmentEndedAt: LocalDateTime?,
    val location: String?,
    val employmentType: String?,
    val sourceName: String?,
)
