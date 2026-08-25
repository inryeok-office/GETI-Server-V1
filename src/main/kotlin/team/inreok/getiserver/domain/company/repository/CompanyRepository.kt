package team.inreok.getiserver.domain.company.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.inreok.getiserver.domain.company.entity.Company
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.company.entity.type.MouStatus

interface CompanyRepository : JpaRepository<Company, Long> {
    // 삭제된 기업(deletedAt != null)은 조회 대상이 아니다. Soft Delete이므로 findById를 그대로
    // 사용하면 삭제된 기업까지 조회되기 때문에 Service는 항상 이 메서드를 사용한다.
    fun findByIdAndDeletedAtIsNull(id: Long): Company?

    // 다른 Domain이 여러 기업의 요약을 한 번에 조회할 때 사용한다(CompanyQuery.findActiveSummaries).
    // 삭제된 기업은 결과에서 빠지므로 호출 측은 요청한 ID가 모두 돌아오지 않을 수 있다고 가정해야 한다.
    fun findAllByIdInAndDeletedAtIsNull(ids: Collection<Long>): List<Company>

    // 같은 이름과 유형의 미삭제 기업이 이미 있는지로 중복을 판정한다(Issue #56 확정 기준).
    // 이름은 대소문자를 무시하고 비교하며, 앞뒤 공백은 Service에서 제거한 뒤 전달한다.
    fun existsByNameIgnoreCaseAndTypeAndDeletedAtIsNull(
        name: String,
        type: CompanyType,
    ): Boolean

    // 수정 시에는 자기 자신을 중복으로 판정하지 않도록 대상 기업을 제외하고 확인한다.
    fun existsByNameIgnoreCaseAndTypeAndDeletedAtIsNullAndIdNot(
        name: String,
        type: CompanyType,
        id: Long,
    ): Boolean

    // Collector의 외부 기업 Find-or-Create(CompanyExternalImportUseCase)가 기존 companies와
    // 동일한 (name, type) 중복 판정 기준(Issue #56, uk_companies_name_type_active)을 재사용할 때
    // 사용한다. existsBy...는 존재 여부만 알려줘 이미 있는 기업의 ID를 알 수 없으므로 별도로 둔다.
    fun findByNameIgnoreCaseAndTypeAndDeletedAtIsNull(
        name: String,
        type: CompanyType,
    ): Company?

    // :query는 Service 계층에서 LIKE Wildcard(%, _)와 Escape 문자(\)를 미리 이스케이프해 전달한다
    // (domain.company.service.escapeLikePattern 참고). null이면 이름 조건을 적용하지 않는다.
    //
    // CAST(:query AS string)이 필요한 이유: :query가 null일 때 Hibernate가 CONCAT/LOWER 안의
    // Parameter 타입을 PostgreSQL에 명확히 알려주지 못하면 PostgreSQL이 이를 bytea로 추론해
    // "function lower(bytea) does not exist"로 Query 자체가 실패한다(검색어 없이 전체 조회하는
    // 가장 기본적인 요청이 항상 500이 되는 실제 회귀, Issue #257). 값이 있을 때의 동작은
    // CAST 추가로 바뀌지 않는다.
    @Query(
        """
        SELECT c FROM Company c
        WHERE c.deletedAt IS NULL
          AND (:query IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%')) ESCAPE '\')
          AND (:companyType IS NULL OR c.type = :companyType)
          AND (:mouStatus IS NULL OR c.mouStatus = :mouStatus)
          AND (:sourceName IS NULL OR c.source = :sourceName)
        """,
    )
    fun search(
        @Param("query") query: String?,
        @Param("companyType") companyType: CompanyType?,
        @Param("mouStatus") mouStatus: MouStatus?,
        @Param("sourceName") sourceName: String?,
        pageable: Pageable,
    ): Page<Company>
}
