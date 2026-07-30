package team.inreok.getiserver.domain.portfolio.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.portfolio.entity.PortfolioRequest

interface PortfolioRequestRepository : JpaRepository<PortfolioRequest, Long>
