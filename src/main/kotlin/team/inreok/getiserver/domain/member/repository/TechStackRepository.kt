package team.inreok.getiserver.domain.member.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.inreok.getiserver.domain.member.entity.TechStack
import team.inreok.getiserver.domain.member.entity.type.TechStackCategory

interface TechStackRepository : JpaRepository<TechStack, Long> {
    @Query(
        """
        SELECT t FROM TechStack t
        WHERE (:query IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :query, '%')))
          AND (:category IS NULL OR t.category = :category)
        ORDER BY t.name ASC
        """,
    )
    fun search(
        @Param("query") query: String?,
        @Param("category") category: TechStackCategory?,
    ): List<TechStack>
}
