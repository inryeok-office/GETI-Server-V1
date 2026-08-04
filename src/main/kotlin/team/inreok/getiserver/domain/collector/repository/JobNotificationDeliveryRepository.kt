package team.inreok.getiserver.domain.collector.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.inreok.getiserver.domain.collector.entity.JobNotificationDelivery
import team.inreok.getiserver.domain.collector.entity.type.JobNotificationDeliveryStatus
import java.time.LocalDateTime

interface JobNotificationDeliveryRepository : JpaRepository<JobNotificationDelivery, Long> {
    fun existsByJobId(jobId: Long): Boolean

    // 아직 한 번도 시도하지 않은(nextRetryAt=NULL) Row와 재시도 시각이 도래한 Row를 함께 조회한다.
    @Query(
        """
        SELECT d FROM JobNotificationDelivery d
        WHERE d.status IN :statuses
        AND (d.nextRetryAt IS NULL OR d.nextRetryAt <= :now)
        ORDER BY d.id ASC
        """,
    )
    fun findDueForRetry(
        @Param("statuses") statuses: List<JobNotificationDeliveryStatus>,
        @Param("now") now: LocalDateTime,
    ): List<JobNotificationDelivery>
}
