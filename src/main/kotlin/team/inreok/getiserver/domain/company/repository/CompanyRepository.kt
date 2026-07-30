package team.inreok.getiserver.domain.company.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.company.entity.Company

interface CompanyRepository : JpaRepository<Company, Long>
