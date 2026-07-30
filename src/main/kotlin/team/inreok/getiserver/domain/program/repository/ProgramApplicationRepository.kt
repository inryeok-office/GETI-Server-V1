package team.inreok.getiserver.domain.program.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.program.entity.ProgramApplication

interface ProgramApplicationRepository : JpaRepository<ProgramApplication, Long>
