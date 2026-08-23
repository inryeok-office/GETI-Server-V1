package team.inreok.getiserver.domain.notification.service.impl

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import team.inreok.getiserver.domain.notification.config.NotificationRetentionProperties
import team.inreok.getiserver.domain.notification.repository.NotificationRepository
import team.inreok.getiserver.domain.notification.service.NotificationRetentionCleanupService
import java.time.LocalDateTime

/**
 * 보관 기한이 지난 알림을 Batch 단위로 Hard Delete한다(Issue #194).
 *
 * ## readAt vs createdAt (읽은 알림 180일 보관 기준)
 *
 * Issue #194 본문은 "읽은 알림은 180일 보관 후 정리한다"라고만 정하고, 그 180일을 `readAt`(읽은
 * 시점)과 `createdAt`(생성 시점) 중 무엇부터 셀지는 Issue 자신이 DECISION_REQUIRED로 남겼다. 이
 * 구현은 **`readAt` 기준**을 택한다.
 *
 * - `Notification` Entity가 이미 `readAt` Column을 갖고 있고(V2 Migration, Issue #187 이전부터
 *   존재), [team.inreok.getiserver.domain.notification.entity.Notification.markAsRead]와
 *   `NotificationRepository.markAllAsRead`(Bulk UPDATE) 둘 다 `isRead=true`로 바꾸는 시점에 항상
 *   `readAt`을 함께 채운다 -- `isRead=true`인데 `readAt`이 비어 있는 Row는 생기지 않는다(불변
 *   조건). `readAt` 기준을 쓰기 위한 별도 Migration이나 Backfill이 필요 없다.
 * - "180일 보관"이라는 표현은 "그 상태로 머문 기간"을 뜻하는데, 읽은 알림이 "그 상태"로 접어든
 *   시점은 생성 시점이 아니라 읽은 시점이다. `createdAt` 기준을 쓰면 "생성 후 오래됐지만 방금 읽은"
 *   알림이 다음 정리 주기에 곧바로 지워질 수 있어(예: 생성 179일 뒤에 읽은 알림은 1일 뒤 삭제),
 *   사용자가 방금 읽은 알림이 갑자기 사라지는 결과로 이어진다. `readAt` 기준은 이 문제가 없다.
 * - 반대로 읽지 않은 알림(365일)은 애초에 `readAt`이 없어 `createdAt`을 쓸 수밖에 없고, Issue
 *   본문도 "사용자가 못 본 알림이라도 1년이 지나면 만료"라고 명시해 `createdAt` 기준이 명확하다.
 *
 * 이 판단이 Issue 의도와 다르면(즉 `createdAt` 기준을 원했다면) 정책 상수를 바꾸는 정도로 되돌릴
 * 수 있다 -- PR 본문에 DECISION_REQUIRED로 남긴다.
 *
 * ## Batch 상한과 동시성
 *
 * 각 정책(Soft Delete/읽음/읽지 않음)마다 `PageRequest.of(0, properties.batchSize)`로 대상 id를
 * 상한만큼만 조회한 뒤 [team.inreok.getiserver.domain.notification.repository.NotificationRepository.deleteAllByIdInBatch]로
 * 지운다(`DiscordDeliveryServiceImpl.processDueDeliveries`와 같은 관례). 상한을 넘는 나머지는 다음
 * Scheduler 실행에서 이어서 처리된다 -- 한 번에 대량 DELETE로 Lock을 오래 잡지 않는다.
 *
 * 여러 Instance가 같은 주기로 동시에 실행돼도 새 분산 Lock Library를 도입하지 않는다
 * (`ProgramCloseScheduler`/`CollectorScheduler`와 동일한 근거, Issue #194 제약). `deleteAllByIdInBatch`는
 * `DELETE FROM notifications WHERE id IN (...)`로 변환되는데, 다른 Instance가 같은 id를 먼저 지웠어도
 * 그 id는 조용히 0건으로 처리될 뿐이라 오류가 나거나 중복 삭제로 이어지지 않는다(자연히 멱등).
 */
@Service
@EnableConfigurationProperties(NotificationRetentionProperties::class)
class NotificationRetentionCleanupServiceImpl(
    private val notificationRepository: NotificationRepository,
    private val properties: NotificationRetentionProperties,
) : NotificationRetentionCleanupService {
    private val log = LoggerFactory.getLogger(NotificationRetentionCleanupServiceImpl::class.java)

    override fun cleanupExpiredNotifications(now: LocalDateTime): Int {
        val softDeletedCount = cleanupSoftDeleted(now)
        val readCount = cleanupRead(now)
        val unreadCount = cleanupUnread(now)
        val total = softDeletedCount + readCount + unreadCount
        if (total > 0) {
            log.info(
                "알림 보관 기한 정리를 수행했습니다(softDeleted={}, read={}, unread={}, total={})",
                softDeletedCount,
                readCount,
                unreadCount,
                total,
            )
        }
        return total
    }

    private fun cleanupSoftDeleted(now: LocalDateTime): Int {
        val cutoff = now.minusDays(properties.softDeletedRetentionDays)
        val ids =
            notificationRepository.findExpiredSoftDeletedIds(cutoff, PageRequest.of(0, properties.batchSize))
        return deleteBatch(ids)
    }

    private fun cleanupRead(now: LocalDateTime): Int {
        val cutoff = now.minusDays(properties.readRetentionDays)
        val ids = notificationRepository.findExpiredReadIds(cutoff, PageRequest.of(0, properties.batchSize))
        return deleteBatch(ids)
    }

    private fun cleanupUnread(now: LocalDateTime): Int {
        val cutoff = now.minusDays(properties.unreadRetentionDays)
        val ids = notificationRepository.findExpiredUnreadIds(cutoff, PageRequest.of(0, properties.batchSize))
        return deleteBatch(ids)
    }

    private fun deleteBatch(ids: List<Long>): Int {
        if (ids.isEmpty()) return 0
        notificationRepository.deleteAllByIdInBatch(ids)
        return ids.size
    }
}
