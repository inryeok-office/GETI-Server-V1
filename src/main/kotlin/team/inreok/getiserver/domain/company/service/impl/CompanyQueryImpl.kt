package team.inreok.getiserver.domain.company.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.company.entity.Company
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.company.query.CompanyQuery
import team.inreok.getiserver.domain.company.query.CompanySummary
import team.inreok.getiserver.domain.company.repository.CompanyRepository
import team.inreok.getiserver.domain.file.link.FileUrlPort

/**
 * 다른 Domain Module에 공개된 조회 계약([CompanyQuery])의 구현이다. `CompanyServiceImpl`에 함께
 * 두지 않고 분리한 이유는 두 가지다 — 이 Class는 Module 밖에서 호출되는 유일한 통로라 책임이
 * 다르고, `CompanyServiceImpl`에 합치면 한 Class가 다루는 함수 수가 지나치게 많아진다.
 */
@Service
class CompanyQueryImpl(
    private val companyRepository: CompanyRepository,
    private val fileUrlPort: FileUrlPort,
) : CompanyQuery {
    @Transactional(readOnly = true)
    override fun findActiveSummary(
        companyId: Long,
        requesterId: Long?,
    ): CompanySummary? {
        val company = companyRepository.findByIdAndDeletedAtIsNull(companyId) ?: return null
        val logoUrl = requesterId?.let { resolveLogoUrl(it, company.logoFileId) }
        return toSummary(company, logoUrl)
    }

    @Transactional(readOnly = true)
    override fun findActiveSummaries(
        companyIds: Collection<Long>,
        requesterId: Long?,
    ): Map<Long, CompanySummary> {
        if (companyIds.isEmpty()) return emptyMap()
        val companies = companyRepository.findAllByIdInAndDeletedAtIsNull(companyIds.toSet())
        // 목록을 한 번에 URL로 바꾼다. 항목마다 단건 발급하면 대상 기업 수만큼 반복된다(N+1,
        // CompanyServiceImpl.search와 같은 이유).
        val logoUrls =
            requesterId?.let { fileUrlPort.presignedImageUrls(it, companies.mapNotNull { c -> c.logoFileId }) }
                ?: emptyMap()
        return companies.associate { company ->
            val summary = toSummary(company, company.logoFileId?.let { logoUrls[it] })
            summary.companyId to summary
        }
    }

    @Transactional(readOnly = true)
    override fun findActiveType(companyId: Long): CompanyType? =
        companyRepository.findByIdAndDeletedAtIsNull(companyId)?.type

    @Transactional(readOnly = true)
    override fun findActiveLogoFileId(companyId: Long): Long? =
        companyRepository.findByIdAndDeletedAtIsNull(companyId)?.logoFileId

    private fun resolveLogoUrl(
        requesterId: Long,
        logoFileId: Long?,
    ): String? {
        val fileId = logoFileId ?: return null
        return fileUrlPort.presignedImageUrls(requesterId, listOf(fileId))[fileId]
    }

    private fun toSummary(
        company: Company,
        logoUrl: String?,
    ): CompanySummary =
        CompanySummary(
            companyId = requireNotNull(company.id) { "저장된 Company는 id를 가져야 합니다." },
            name = company.name,
            logoUrl = logoUrl,
        )
}
