package team.inreok.getiserver.domain.collector.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.inreok.getiserver.domain.collector.entity.JobNotificationDelivery
import team.inreok.getiserver.domain.collector.entity.type.JobNotificationDeliveryStatus
import java.time.LocalDateTime

interface JobNotificationDeliveryRepository : JpaRepository<JobNotificationDelivery, Long> {
    fun existsByJobId(jobId: Long): Boolean

    /** 서버 재시작 시 SENDING 상태로 멈춘 Row를 찾아 PENDING으로 되돌리는 용도로만 사용한다. */
    fun findAllByStatus(status: JobNotificationDeliveryStatus): List<JobNotificationDelivery>

    // PENDING(아직 한 번도 시도하지 않음)은 nextRetryAt과 무관하게 대상이고, FAILED는 재시도
    // 가능한 실패(nextRetryAt이 명시적으로 설정됨)이면서 그 시각이 지난 Row만 대상이다.
    // markFailedFinal이 만드는 최종 실패 Row(status=FAILED, nextRetryAt=NULL)는 PENDING과
    // 구분되지 않으면 계속 재조회되어 정책("400/401/403 등은 재시도하지 않는다")과 다르게
    // 재시도된다 — 그래서 FAILED는 nextRetryAt IS NOT NULL을 반드시 함께 확인한다
    // (PR #68 Code Review Finding #1, JobNotificationDeliveryRepositoryIntegrationTest 참고).
    @Query(
        """
        SELECT d FROM JobNotificationDelivery d
        WHERE d.status = team.inreok.getiserver.domain.collector.entity.type.JobNotificationDeliveryStatus.PENDING
           OR (
                d.status = team.inreok.getiserver.domain.collector.entity.type.JobNotificationDeliveryStatus.FAILED
                AND d.nextRetryAt IS NOT NULL
                AND d.nextRetryAt <= :now
           )
        ORDER BY d.id ASC
        """,
    )
    fun findDueForRetry(
        @Param("now") now: LocalDateTime,
    ): List<JobNotificationDelivery>
}
