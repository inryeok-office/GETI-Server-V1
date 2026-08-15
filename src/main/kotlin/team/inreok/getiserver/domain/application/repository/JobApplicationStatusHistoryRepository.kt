package team.inreok.getiserver.domain.application.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.application.entity.JobApplicationStatusHistory

interface JobApplicationStatusHistoryRepository : JpaRepository<JobApplicationStatusHistory, Long> {
    // 지원서 상세 조회(학생 본인/교사·개발자)에서 이력을 오래된 순으로 보여준다(Issue #133).
    fun findByApplicationIdOrderByCreatedAtAsc(applicationId: Long): List<JobApplicationStatusHistory>
}
