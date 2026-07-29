package team.inreok.getiserver.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface PersistenceProbeRepository : JpaRepository<PersistenceProbeEntity, Long>
