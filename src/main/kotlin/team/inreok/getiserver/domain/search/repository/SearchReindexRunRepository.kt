package team.inreok.getiserver.domain.search.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.search.entity.SearchReindexRun
import team.inreok.getiserver.domain.search.entity.type.SearchReindexStatus

interface SearchReindexRunRepository : JpaRepository<SearchReindexRun, Long> {
    fun existsByStatusIn(statuses: Collection<SearchReindexStatus>): Boolean
}
