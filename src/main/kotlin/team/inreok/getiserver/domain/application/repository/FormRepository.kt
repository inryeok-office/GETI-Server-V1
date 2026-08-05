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
    // 소유권 검증은 findById로 조회한 뒤 Service에서 ownerMemberId를 비교하는 방식으로 한다
    // (존재하지 않음=FORM_NOT_FOUND와 소유자 아님=NOT_FORM_OWNER/FORM_NOT_OWNED를 구분해야
    // 하기 때문). 코드 리뷰에서 지적된 미사용 findByIdAndOwnerMemberId(id, ownerMemberId)는
    // 제거했다(PR #77 Finding #2).

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
