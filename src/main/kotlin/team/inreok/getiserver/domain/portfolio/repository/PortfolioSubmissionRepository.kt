package team.inreok.getiserver.domain.portfolio.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.portfolio.entity.PortfolioSubmission

interface PortfolioSubmissionRepository : JpaRepository<PortfolioSubmission, Long> {
    fun findByRequestIdAndMemberId(
        requestId: Long,
        memberId: Long,
    ): PortfolioSubmission?
}
