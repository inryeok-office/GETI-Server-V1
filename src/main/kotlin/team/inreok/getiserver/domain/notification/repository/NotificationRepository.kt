package team.inreok.getiserver.domain.notification.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.inreok.getiserver.domain.notification.entity.Notification
import team.inreok.getiserver.domain.notification.entity.type.NotificationType
import java.time.LocalDateTime

// V38 Migration(V35에서 재번호화, Migration 버전 충돌 수정 -- Issue #251)이 추가한 각 Partial
// Index(idx_notifications_deleted_at_retention/idx_notifications_unread_retention/
// idx_notifications_read_retention)와 정확히 같은 WHERE 조건을 써야 Query Planner가 그 Index를
// 탄다(NotificationRetentionCleanupServiceImpl 참고).

interface NotificationRepository : JpaRepository<Notification, Long> {
    /**
     * 내 알림 목록이다. `:isRead`, `:type`은 null이면 조건을 적용하지 않는다(ProgramRepository
     * .search와 동일한 관례). 정렬은 Pageable이 아니라 이 Query가 고정한다 — 원본 요구사항 문서
     * 4절이 "createdAt DESC, 같은 시각이면 id DESC"로 정해 두었고, 클라이언트가 정렬을 바꿀 수
     * 있게 할 이유가 없기 때문이다.
     */
    @Query(
        """
        SELECT n FROM Notification n
        WHERE n.recipientMemberId = :memberId
          AND n.deletedAt IS NULL
          AND (:isRead IS NULL OR n.isRead = :isRead)
          AND (:type IS NULL OR n.type = :type)
        ORDER BY n.createdAt DESC, n.id DESC
        """,
    )
    fun findMyNotifications(
        @Param("memberId") memberId: Long,
        @Param("isRead") isRead: Boolean?,
        @Param("type") type: NotificationType?,
        pageable: Pageable,
    ): Page<Notification>

    fun countByRecipientMemberIdAndIsReadFalseAndDeletedAtIsNull(recipientMemberId: Long): Long

    /**
     * Idempotency Identity(recipientMemberId + sourceEventType + sourceEventId, Issue #193)로
     * 기존 알림을 찾는다. `NotificationServiceImpl.create`가 UNIQUE 제약 위반을 잡은 뒤 이미
     * 존재하는 알림을 재사용할 때만 쓴다 — Soft Delete된 알림도 그대로 찾아야 하므로
     * `deletedAt IS NULL` 조건을 걸지 않는다(삭제 여부와 무관하게 같은 원본 Event는 재생성하지
     * 않는다는 확정 계약).
     */
    fun findByRecipientMemberIdAndSourceEventTypeAndSourceEventId(
        recipientMemberId: Long,
        sourceEventType: String,
        sourceEventId: Long,
    ): Notification?

    /**
     * 읽지 않은 알림을 한 번의 UPDATE로 모두 읽음 처리하고 갱신 건수를 반환한다. 한 사용자의
     * 알림 건수에 상한이 없어 Entity를 모두 불러와 수정하는 방식은 쓰지 않는다.
     *
     * Bulk JPQL은 Hibernate의 `@UpdateTimestamp`를 거치지 않으므로 `updatedAt`을 직접 갱신한다.
     * `clearAutomatically`/`flushAutomatically`로 같은 Transaction의 영속성 Context와 어긋나지
     * 않게 한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE Notification n
        SET n.isRead = true, n.readAt = :readAt, n.updatedAt = :readAt
        WHERE n.recipientMemberId = :memberId
          AND n.isRead = false
          AND n.deletedAt IS NULL
        """,
    )
    fun markAllAsRead(
        @Param("memberId") memberId: Long,
        @Param("readAt") readAt: LocalDateTime,
    ): Int

    /**
     * Soft Delete된 지 오래된 알림(Retention #194 확정 계약: `deletedAt` 기준 30일)의 id다. 이후
     * [deleteAllByIdInBatch]로 Hard Delete한다. 상한(`pageable`)을 넘는 만큼은 다음 Scheduler
     * 실행에서 이어서 처리된다.
     */
    @Query(
        """
        SELECT n.id FROM Notification n
        WHERE n.deletedAt IS NOT NULL AND n.deletedAt <= :cutoff
        ORDER BY n.id ASC
        """,
    )
    fun findExpiredSoftDeletedIds(
        @Param("cutoff") cutoff: LocalDateTime,
        pageable: Pageable,
    ): List<Long>

    /**
     * 읽은 지 오래된(아직 Soft Delete 안 된) 알림의 id다(Retention #194 확정 계약: `readAt` 기준
     * 180일 -- `isRead=true`인 Row는 항상 `readAt`이 함께 채워져 있다는 전제는
     * [team.inreok.getiserver.domain.notification.entity.Notification.markAsRead]와
     * `NotificationRepository.markAllAsRead`가 보장한다).
     */
    @Query(
        """
        SELECT n.id FROM Notification n
        WHERE n.deletedAt IS NULL AND n.isRead = true AND n.readAt <= :cutoff
        ORDER BY n.id ASC
        """,
    )
    fun findExpiredReadIds(
        @Param("cutoff") cutoff: LocalDateTime,
        pageable: Pageable,
    ): List<Long>

    /**
     * 읽지 않은 채로 오래된(아직 Soft Delete 안 된) 알림의 id다(Retention #194 확정 계약:
     * `createdAt` 기준 365일).
     */
    @Query(
        """
        SELECT n.id FROM Notification n
        WHERE n.deletedAt IS NULL AND n.isRead = false AND n.createdAt <= :cutoff
        ORDER BY n.id ASC
        """,
    )
    fun findExpiredUnreadIds(
        @Param("cutoff") cutoff: LocalDateTime,
        pageable: Pageable,
    ): List<Long>
}
