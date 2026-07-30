package team.inreok.getiserver.domain.program.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.program.entity.Program

interface ProgramRepository : JpaRepository<Program, Long>
