package team.inreok.getiserver.domain.collector.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.collector.entity.CollectionRunError

interface CollectionRunErrorRepository : JpaRepository<CollectionRunError, Long> {
    fun findAllByRunIdOrderByOccurredAtAsc(runId: Long): List<CollectionRunError>

    fun findFirstByRunIdOrderByOccurredAtDesc(runId: Long): CollectionRunError?
}
