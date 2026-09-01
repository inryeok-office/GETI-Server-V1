package team.inreok.getiserver.domain.member.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.inreok.getiserver.domain.member.entity.TechStack
import team.inreok.getiserver.domain.member.entity.type.TechStackCategory

interface TechStackRepository : JpaRepository<TechStack, Long> {
    // :query는 Service 계층에서 LIKE Wildcard(%, _)와 Escape 문자(\)를 미리 이스케이프해 전달한다
    // (검색어에 %/_가 포함되어도 문자 그대로 매칭하기 위함, ESCAPE '\' 참고). null이면 이름
    // 조건을 적용하지 않는다.
    //
    // CAST(:query AS string)이 필요한 이유: :query가 null일 때 Hibernate가 CONCAT/LOWER 안의
    // Parameter 타입을 PostgreSQL에 명확히 알려주지 못하면 PostgreSQL이 이를 bytea로 추론해
    // "function lower(bytea) does not exist"로 Query 자체가 실패한다(검색어 없이 전체 조회하는
    // 가장 기본적인 요청이 항상 500이 되는 실제 회귀, CompanyRepository.search와 동일한 취약
    // 형태, Issue #257/#259). 값이 있을 때의 동작은 CAST 추가로 바뀌지 않는다.
    @Query(
        """
        SELECT t FROM TechStack t
        WHERE (:query IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%')) ESCAPE '\')
          AND (:category IS NULL OR t.category = :category)
        ORDER BY t.name ASC
        """,
    )
    fun search(
        @Param("query") query: String?,
        @Param("category") category: TechStackCategory?,
    ): List<TechStack>
}
