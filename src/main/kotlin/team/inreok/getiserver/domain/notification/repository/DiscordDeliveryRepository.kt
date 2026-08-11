package team.inreok.getiserver.domain.notification.repository

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.notification.entity.DiscordDelivery
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryStatus
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryTargetType
import java.time.LocalDateTime

interface DiscordDeliveryRepository : JpaRepository<DiscordDelivery, Long> {
    fun findByIdempotencyKey(idempotencyKey: String): DiscordDelivery?

    /**
     * 대상별 최신 Delivery다. 상태 조회 API(후속 요구사항 문서 §36)가 붙는 PR에서 쓴다.
     */
    fun findFirstByTargetTypeAndTargetIdOrderByIdDesc(
        targetType: DiscordDeliveryTargetType,
        targetId: Long,
    ): DiscordDelivery?

    /**
     * Worker가 이번 Sweep에서 처리할 후보 Id다(§30). 아직 한 번도 시도하지 않은 Row는
     * `next_retry_at`이 null이고, 자동 재시도 대기 중인 Row는 그 시각이 지나야 대상이 된다.
     *
     * Entity가 아니라 Id만 가져온다 -- 실제 선점은 [claim]의 조건부 UPDATE가 하므로, 여기서
     * 읽은 Entity는 다른 인스턴스가 이미 가져갔을 수 있어 그대로 쓰면 안 되기 때문이다.
     */
    @Query(
        """
        SELECT d.id FROM DiscordDelivery d
        WHERE d.status = :status
          AND (d.nextRetryAt IS NULL OR d.nextRetryAt <= :now)
        ORDER BY d.id ASC
        """,
    )
    fun findDueIds(
        @Param("status") status: DiscordDeliveryStatus,
        @Param("now") now: LocalDateTime,
        pageable: Pageable,
    ): List<Long>

    /**
     * Delivery 하나를 선점한다. 영향 행 수가 1일 때만 이 인스턴스가 처리 권한을 얻는다.
     *
     * `WHERE status = PENDING` 조건이 원자적 Compare-And-Set 역할을 해, 여러 인스턴스나
     * 수동 재시도가 같은 Row를 동시에 집어도 하나만 통과한다. Row를 잠근 채 수 초짜리 HTTP
     * 호출을 감싸는 Pessimistic Lock과 달리, 이 UPDATE는 즉시 Commit되고 Bot 호출은 Lock
     * 밖에서 수행된다(spring-boot.md: 느린 I/O를 Transaction 안에서 수행 금지).
     *
     * Bulk JPQL은 `@UpdateTimestamp`를 거치지 않으므로 `updatedAt`을 직접 갱신한다
     * (`NotificationRepository.markAllAsRead`와 같은 이유).
     *
     * 호출부(Worker)가 `@Transactional`일 수 없어 -- 그 안에서 수 초짜리 Bot HTTP 호출이
     * 일어나면 Transaction이 그만큼 길어진다 -- 이 메서드에 Transaction 경계를 직접 둔다.
     * 선점만 즉시 Commit하고 Bot 호출은 그 밖에서 수행한다.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE DiscordDelivery d
        SET d.status = :processing, d.processingStartedAt = :now, d.updatedAt = :now
        WHERE d.id = :id AND d.status = :pending
        """,
    )
    fun claim(
        @Param("id") id: Long,
        @Param("pending") pending: DiscordDeliveryStatus,
        @Param("processing") processing: DiscordDeliveryStatus,
        @Param("now") now: LocalDateTime,
    ): Int

    /**
     * 임계값을 넘도록 PROCESSING에 머문 Row를 재시도 대기로 되돌리고 회수 건수를 반환한다(§31).
     *
     * `processingStartedAt < :threshold` 조건 덕분에 다른 인스턴스가 **지금 정상적으로 전송
     * 중인** Row는 건드리지 않는다. 자동 재시도 횟수를 늘리지 않는 이유는, 이 Row가 Bot의
     * 판단을 받아 실패한 것이 아니라 서버가 결과를 기록하지 못한 채 중단된 것이기 때문이다.
     *
     * [claim]과 같은 이유로 Transaction 경계를 이 메서드에 둔다.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE DiscordDelivery d
        SET d.status = :pending, d.processingStartedAt = NULL, d.updatedAt = :now
        WHERE d.status = :processing AND d.processingStartedAt < :threshold
        """,
    )
    fun recoverStaleProcessing(
        @Param("pending") pending: DiscordDeliveryStatus,
        @Param("processing") processing: DiscordDeliveryStatus,
        @Param("threshold") threshold: LocalDateTime,
        @Param("now") now: LocalDateTime,
    ): Int
}
