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
}
