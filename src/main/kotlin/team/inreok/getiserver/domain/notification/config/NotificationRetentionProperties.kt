package team.inreok.getiserver.domain.notification.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 인앱 알림 보관·정리 정책 설정이다(`application.yaml`의 `app.notification.retention.*`,
 * Issue #194).
 *
 * `domain.audit`의 Audit Log Retention과는 **완전히 별개의 설정 Namespace**다 -- Audit Log는
 * 감사·통계 목적의 장기 보존 요구가 확인되지 않아 이번 계약에 포함하지 않으므로, 같은 Scheduler나
 * 같은 설정 Key로 묶지 않는다(Issue #194 "확정 계약" 참고).
 *
 * 세 보관 기간은 서로 다른 대상(Soft Delete/읽음/읽지 않음)에 적용되며 값을 Source Code에 고정하지
 * 않고 환경 변수로 재정의할 수 있다 -- 값이 바뀌어도 코드 수정·재배포가 필요 없다.
 */
@ConfigurationProperties(prefix = "app.notification.retention")
data class NotificationRetentionProperties(
    /**
     * Soft Delete(`deletedAt IS NOT NULL`)된 알림을 `deletedAt` 기준 며칠 뒤 Hard Delete할지다.
     */
    val softDeletedRetentionDays: Long = DEFAULT_SOFT_DELETED_RETENTION_DAYS,
    /**
     * 읽은 알림(`isRead=true`, 아직 Soft Delete 안 됨)을 `readAt` 기준 며칠 뒤 Hard Delete할지다.
     * `createdAt`이 아니라 `readAt`을 기준으로 삼은 이유는
     * [team.inreok.getiserver.domain.notification.service.impl.NotificationRetentionCleanupServiceImpl]의
     * KDoc을 따른다.
     */
    val readRetentionDays: Long = DEFAULT_READ_RETENTION_DAYS,
    /**
     * 읽지 않은 알림(`isRead=false`, 아직 Soft Delete 안 됨)을 `createdAt` 기준 며칠 뒤 Hard
     * Delete할지다.
     */
    val unreadRetentionDays: Long = DEFAULT_UNREAD_RETENTION_DAYS,
    /**
     * 한 번의 Scheduler 실행에서 각 정책(Soft Delete/읽음/읽지 않음)별로 Hard Delete할 최대
     * 건수다. 대량 DELETE로 Lock을 오래 잡지 않기 위한 상한이며, 상한을 넘는 나머지는 다음 실행에서
     * 이어서 처리된다.
     */
    val batchSize: Int = DEFAULT_BATCH_SIZE,
    /**
     * Scheduler 실행 주기(ms). `ProgramCloseScheduler`(`interval-ms`)와 같은 관례다. Hard Delete는
     * 되돌릴 수 없어 촘촘하게 돌 필요가 없으므로 1시간을 기본값으로 둔다.
     */
    val schedulerIntervalMs: Long = DEFAULT_SCHEDULER_INTERVAL_MS,
) {
    companion object {
        const val DEFAULT_SOFT_DELETED_RETENTION_DAYS = 30L
        const val DEFAULT_READ_RETENTION_DAYS = 180L
        const val DEFAULT_UNREAD_RETENTION_DAYS = 365L
        const val DEFAULT_BATCH_SIZE = 500
        const val DEFAULT_SCHEDULER_INTERVAL_MS = 3_600_000L
    }
}
