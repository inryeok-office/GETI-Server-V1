package team.inreok.getiserver.domain.application.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.application.entity.FormVersion

interface FormVersionRepository : JpaRepository<FormVersion, Long> {
    fun findByFormIdAndVersion(
        formId: Long,
        version: Int,
    ): FormVersion?

    fun findByFormIdIn(formIds: Collection<Long>): List<FormVersion>

    // Form 상세 조회는 항상 현재(최신) 버전의 필드 구조를 보여준다(요구사항 5.4절).
    fun findTopByFormIdOrderByVersionDesc(formId: Long): FormVersion?
}
