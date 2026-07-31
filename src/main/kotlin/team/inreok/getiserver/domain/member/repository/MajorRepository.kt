package team.inreok.getiserver.domain.member.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.member.entity.Major

interface MajorRepository : JpaRepository<Major, Long> {
    fun findAllByOrderByNameAsc(): List<Major>

    fun findAllByActiveTrueOrderByNameAsc(): List<Major>
}
