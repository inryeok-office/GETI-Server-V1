package team.inreok.getiserver.domain.member.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.member.entity.Major

interface MajorRepository : JpaRepository<Major, Long> {
    fun findAllByOrderByNameAsc(): List<Major>

    fun findAllByActiveTrueOrderByNameAsc(): List<Major>

    // PATCH /me/majors는 폐지(active=false)된 전공을 새로 선택하지 못하게 막는다(코드 리뷰 Minor 반영,
    // 사용자 결정: 비활성 전공 선택 차단). active=false인 majorId는 존재하지 않는 것과 동일하게
    // MAJOR_NOT_FOUND로 처리된다.
    fun findAllByIdInAndActiveTrue(ids: List<Long>): List<Major>
}
