package team.inreok.getiserver.domain.notification.service

import java.time.LocalDateTime

interface NotificationRetentionCleanupService {
    /**
     * 보관 기한이 지난 알림을 정책별로 Batch 상한 안에서 Hard Delete하고, 이번 실행에서 실제로
     * 지운 총 건수를 반환한다(Issue #194). 세 정책(Soft Delete/읽음/읽지 않음)은 서로 배타적인
     * 조건([team.inreok.getiserver.domain.notification.repository.NotificationRepository]의 각
     * `findExpired*Ids` 참고)이라 같은 Row가 두 번 지워지지 않는다.
     *
     * [now]를 호출부(Scheduler)에서 주입받아, Test가 시각을 직접 통제할 수 있게 한다
     * (`ProgramCloseScheduler`/`ProgramCloseService`와 같은 방식).
     */
    fun cleanupExpiredNotifications(now: LocalDateTime): Int
}
