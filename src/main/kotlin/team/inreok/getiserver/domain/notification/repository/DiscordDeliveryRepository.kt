package team.inreok.getiserver.domain.notification.repository

import org.springframework.data.domain.Page
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
    fun findFirstByOrderByUpdatedAtDescIdDesc(): DiscordDelivery?

    fun findByIdempotencyKey(idempotencyKey: String): DiscordDelivery?

    /**
     * 대상별 최신 Delivery다. 상태 조회 API(후속 요구사항 문서 §36)가 붙는 PR에서 쓴다.
     */
    fun findFirstByTargetTypeAndTargetIdOrderByIdDesc(
        targetType: DiscordDeliveryTargetType,
        targetId: Long,
    ): DiscordDelivery?

    /**
     * 관리자 횡단 목록이다(Issue #206). [status]를 지정하지 않으면 전체를 돌려준다.
     *
     * 정렬을 `id DESC`로 고정한다 -- `id`는 BIGSERIAL이라 `created_at DESC`와 순서가 같으면서 PK
     * Index를 그대로 쓸 수 있다. 클라이언트가 보낸 Sort는 이 ORDER BY와 충돌하므로 호출부가
     * Pageable에서 제거한 뒤 넘긴다(`NotificationRepository.findMyNotifications`와 같은 관례).
     */
    @Query(
        """
        SELECT d FROM DiscordDelivery d
        WHERE (:status IS NULL OR d.status = :status)
        ORDER BY d.id DESC
        """,
    )
    fun findRecent(
        @Param("status") status: DiscordDeliveryStatus?,
        pageable: Pageable,
    ): Page<DiscordDelivery>

    /**
     * 주어진 대상들에 대해 "그 대상의 가장 최근 Delivery"인 Row의 id다(Issue #206).
     *
     * 수동 재시도 API가 대상별 최신 Row 하나만 재시도하므로
     * ([findFirstByTargetTypeAndTargetIdOrderByIdDesc]), 목록의 `canRetry`도 그 Row에만 true여야
     * 한다. 그렇지 않으면 관리자가 "CREATE 실패" 행에서 재시도를 눌렀을 때 서버가 그 뒤에 생긴
     * "UPDATE 성공" Row를 건드려 `DISCORD_DELIVERY_NOT_RETRYABLE`로 거절한다.
     *
     * GROUP BY + MAX(id) 결과를 받으려면 Projection 타입이 필요한데 이 저장소에는 전례가 없어,
     * 상관 서브쿼리로 id만 돌려받는다.
     *
     * [targetTypes]와 [targetIds]를 각각 IN으로 걸어서, (JOB,3)과 (PROGRAM,5)만 필요할 때
     * (JOB,5)의 최신 Row도 함께 걸릴 수 있다. 그 여분 id는 현재 Page에 없는 Row라 판정 결과를
     * 바꾸지 않는다 -- Tuple IN 문법에 의존하지 않으려고 택한 방식이다.
     */
    @Query(
        """
        SELECT d.id FROM DiscordDelivery d
        WHERE d.targetType IN :targetTypes
          AND d.targetId IN :targetIds
          AND NOT EXISTS (
              SELECT 1 FROM DiscordDelivery o
              WHERE o.targetType = d.targetType AND o.targetId = d.targetId AND o.id > d.id
          )
        """,
    )
    fun findLatestDeliveryIds(
        @Param("targetTypes") targetTypes: Set<DiscordDeliveryTargetType>,
        @Param("targetIds") targetIds: Set<Long>,
    ): List<Long>

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
