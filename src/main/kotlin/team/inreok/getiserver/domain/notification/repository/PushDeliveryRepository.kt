package team.inreok.getiserver.domain.notification.repository

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.notification.entity.PushDelivery
import team.inreok.getiserver.domain.notification.entity.type.PushDeliveryStatus
import java.time.LocalDateTime

/**
 * `DiscordDeliveryRepository`의 claim/recoverStaleProcessing/findDueIds와 같은 구조를 그대로
 * 따른다(같은 이유 -- 여러 Instance가 동시에 Sweep을 돌아도 한 Row는 하나만 처리한다).
 */
interface PushDeliveryRepository : JpaRepository<PushDelivery, Long> {
    /**
     * Worker가 이번 Sweep에서 처리할 후보 Id다. 아직 한 번도 시도하지 않은 Row는 `nextRetryAt`이
     * null이고, 재시도 대기 중인 Row는 그 시각이 지나야 대상이 된다.
     */
    @Query(
        """
        SELECT p.id FROM PushDelivery p
        WHERE p.status = :status
          AND (p.nextRetryAt IS NULL OR p.nextRetryAt <= :now)
        ORDER BY p.id ASC
        """,
    )
    fun findDueIds(
        @Param("status") status: PushDeliveryStatus,
        @Param("now") now: LocalDateTime,
        pageable: Pageable,
    ): List<Long>

    /**
     * Push 전달 하나를 선점한다. 영향 행 수가 1일 때만 이 Instance가 처리 권한을 얻는다
     * (`DiscordDeliveryRepository.claim`과 같은 이유·같은 방식).
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE PushDelivery p
        SET p.status = :processing, p.processingStartedAt = :now, p.updatedAt = :now
        WHERE p.id = :id AND p.status = :pending
        """,
    )
    fun claim(
        @Param("id") id: Long,
        @Param("pending") pending: PushDeliveryStatus,
        @Param("processing") processing: PushDeliveryStatus,
        @Param("now") now: LocalDateTime,
    ): Int

    /**
     * 임계값을 넘도록 PROCESSING에 머문 Row를 재시도 대기로 되돌리고 회수 건수를 반환한다
     * (`DiscordDeliveryRepository.recoverStaleProcessing`과 같은 이유).
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE PushDelivery p
        SET p.status = :pending, p.processingStartedAt = NULL, p.updatedAt = :now
        WHERE p.status = :processing AND p.processingStartedAt < :threshold
        """,
    )
    fun recoverStaleProcessing(
        @Param("pending") pending: PushDeliveryStatus,
        @Param("processing") processing: PushDeliveryStatus,
        @Param("threshold") threshold: LocalDateTime,
        @Param("now") now: LocalDateTime,
    ): Int
}
