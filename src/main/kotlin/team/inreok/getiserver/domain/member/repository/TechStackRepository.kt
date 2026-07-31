package team.inreok.getiserver.domain.member.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.inreok.getiserver.domain.member.entity.TechStack
import team.inreok.getiserver.domain.member.entity.type.TechStackCategory

interface TechStackRepository : JpaRepository<TechStack, Long> {
    // :query는 Service 계층에서 LIKE Wildcard(%, _)와 Escape 문자(\)를 미리 이스케이프해 전달한다
    // (검색어에 %/_가 포함되어도 문자 그대로 매칭하기 위함, ESCAPE '\' 참고).
    @Query(
        """
        SELECT t FROM TechStack t
        WHERE (:query IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '\')
          AND (:category IS NULL OR t.category = :category)
        ORDER BY t.name ASC
        """,
    )
    fun search(
        @Param("query") query: String?,
        @Param("category") category: TechStackCategory?,
    ): List<TechStack>
}
