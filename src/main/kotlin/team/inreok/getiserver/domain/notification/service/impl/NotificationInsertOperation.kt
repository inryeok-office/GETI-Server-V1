package team.inreok.getiserver.domain.notification.service.impl

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.notification.dto.NotificationCreateCommand
import team.inreok.getiserver.domain.notification.entity.Notification
import team.inreok.getiserver.domain.notification.repository.NotificationRepository

/**
 * `Notification` Insert 자체만 담당하는 전용 협력자다(Issue #193).
 *
 * `NotificationServiceImpl.create`와 별도 Class로 분리한 이유는 두 가지 제약이 겹치기 때문이다.
 *
 * 1. Insert는 반드시 `REQUIRES_NEW`로 실행해야 한다 -- `create`의 유일한 호출자인
 *    `@TransactionalEventListener(AFTER_COMMIT)` Listener 시점에는 원본 Transaction이 이미
 *    Commit됐지만 Thread에 아직 바인딩된 상태라, 기본 `REQUIRED`로 두면 새 Insert가 그 끝난
 *    Transaction에 참여해 예외 없이 조용히 사라진다(Issue #118). Kotlin/Spring AOP는
 *    Self-invocation(같은 Class 안에서 `this.otherMethod()` 호출)에는 Proxy를 거치지 않아
 *    `@Transactional`이 적용되지 않으므로, `NotificationServiceImpl` 안의 다른 Method로
 *    두면 이 보장이 사라진다 -- 그래서 별도 Spring Bean으로 분리했다.
 * 2. UNIQUE 제약(`uk_notifications_recipient_source_event`) 위반을 잡은 뒤 기존 Row를 다시
 *    조회하려면, 그 조회가 실패한 Insert와 **다른** Transaction에서 실행돼야 한다.
 *    PostgreSQL은 한 Statement가 오류로 실패하면 그 Transaction 전체를 Abort 상태로 만들어
 *    같은 Transaction 안에서의 후속 Query(SELECT 포함)도 모두 거부한다. 이 Class는 Insert
 *    실패를 스스로 잡지 않고 그대로 다시 던진다 -- `REQUIRES_NEW`로 실행되므로 예외가 이
 *    Method를 빠져나가는 순간 Spring이 이 Transaction만 Rollback하고 물러나며(바깥
 *    Transaction에는 영향 없음), 호출자(`NotificationServiceImpl.create`)는 이 Transaction이
 *    완전히 끝난 뒤에야 예외를 받는다. 그래서 호출자가 잡은 뒤 수행하는 기존 Row 조회는 항상
 *    새 Transaction에서 실행되어 Abort 문제를 겪지 않는다.
 */
@Component
class NotificationInsertOperation(
    private val notificationRepository: NotificationRepository,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun insert(command: NotificationCreateCommand): Long {
        val notification =
            Notification(
                recipientMemberId = command.recipientMemberId,
                type = command.type,
                title = command.title,
                content = command.content,
            ).apply {
                targetType = command.targetType
                targetId = command.targetId
                sourceEventType = command.sourceEventType
                sourceEventId = command.sourceEventId
            }
        val saved = notificationRepository.save(notification)
        return requireNotNull(saved.id) { "저장된 Notification은 id를 가져야 합니다." }
    }
}
