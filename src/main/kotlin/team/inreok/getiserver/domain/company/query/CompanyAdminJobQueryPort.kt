package team.inreok.getiserver.domain.company.query

import org.springframework.modulith.NamedInterface
import java.time.LocalDateTime

@NamedInterface
interface CompanyAdminJobQueryPort {
    fun findByCompanyId(companyId: Long): List<CompanyAdminJobSnapshot>
}

@NamedInterface
data class CompanyAdminJobSnapshot(
    val jobId: Long,
    val title: String,
    val postingType: String,
    val status: String,
    val recruitmentEndedAt: LocalDateTime?,
)
