package team.inreok.getiserver.domain.ai.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.inreok.getiserver.domain.ai.entity.JobAiAnalysis

interface JobAiAnalysisRepository : JpaRepository<JobAiAnalysis, Long> {
    /**
     * 수동 재분석 요청과 비동기 Worker의 상태 전이(PENDING -> PROCESSING 등)가 동시에 일어나도
     * 같은 Job을 중복으로 OpenAI에 보내지 않도록 Row 단위로 Lock을 건다(GETI Notion AI Analysis
     * Phase 1 계약 "Concurrent Worker/중복 Event 방지"). 두 Transaction 모두 이 Method로 Row를
     * 잠근 뒤에만 상태를 읽고 바꾸므로, 먼저 Lock을 획득한 쪽이 끝날 때까지 나머지는 대기한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM JobAiAnalysis a WHERE a.jobId = :jobId")
    fun findByIdForUpdate(
        @Param("jobId") jobId: Long,
    ): JobAiAnalysis?
}
