package team.inreok.getiserver.domain.company.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.company.entity.Company
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.company.query.CompanyQuery
import team.inreok.getiserver.domain.company.query.CompanySummary
import team.inreok.getiserver.domain.company.repository.CompanyRepository

/**
 * 다른 Domain Module에 공개된 조회 계약([CompanyQuery])의 구현이다. `CompanyServiceImpl`에 함께
 * 두지 않고 분리한 이유는 두 가지다 — 이 Class는 Module 밖에서 호출되는 유일한 통로라 책임이
 * 다르고, `CompanyServiceImpl`에 합치면 한 Class가 다루는 함수 수가 지나치게 많아진다.
 */
@Service
class CompanyQueryImpl(
    private val companyRepository: CompanyRepository,
) : CompanyQuery {
    @Transactional(readOnly = true)
    override fun findActiveSummary(companyId: Long): CompanySummary? =
        companyRepository.findByIdAndDeletedAtIsNull(companyId)?.let(::toSummary)

    @Transactional(readOnly = true)
    override fun findActiveSummaries(companyIds: Collection<Long>): Map<Long, CompanySummary> {
        if (companyIds.isEmpty()) return emptyMap()
        return companyRepository
            .findAllByIdInAndDeletedAtIsNull(companyIds.toSet())
            .associate { toSummary(it).let { summary -> summary.companyId to summary } }
    }

    @Transactional(readOnly = true)
    override fun findActiveType(companyId: Long): CompanyType? =
        companyRepository.findByIdAndDeletedAtIsNull(companyId)?.type

    private fun toSummary(company: Company): CompanySummary =
        CompanySummary(
            companyId = requireNotNull(company.id) { "저장된 Company는 id를 가져야 합니다." },
            name = company.name,
        )
}
