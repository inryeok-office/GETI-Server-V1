package team.inreok.getiserver.domain.application.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.inreok.getiserver.domain.application.entity.Form
import team.inreok.getiserver.domain.application.entity.type.FormStatus
import team.inreok.getiserver.domain.application.entity.type.FormType

interface FormRepository : JpaRepository<Form, Long> {
    // 다른 사용자의 양식은 검색·조회·수정·복제·활성화·보관이 모두 불가능해야 한다(요구사항 5.1절).
    // 소유자 검증을 Repository 조회 조건에 포함시켜, Service가 이 메서드로 찾지 못하면 항상
    // "존재하지 않음" 또는 "소유자 아님"으로만 분기하도록 한다.
    fun findByIdAndOwnerMemberId(
        id: Long,
        ownerMemberId: Long,
    ): Form?

    // :formType, :status는 null이면 조건을 적용하지 않는다(CompanyRepository.search와 동일한 관례).
    @Query(
        """
        SELECT f FROM Form f
        WHERE f.ownerMemberId = :ownerMemberId
          AND (:formType IS NULL OR f.formType = :formType)
          AND (:status IS NULL OR f.status = :status)
        """,
    )
    fun search(
        @Param("ownerMemberId") ownerMemberId: Long,
        @Param("formType") formType: FormType?,
        @Param("status") status: FormStatus?,
        pageable: Pageable,
    ): Page<Form>
}
