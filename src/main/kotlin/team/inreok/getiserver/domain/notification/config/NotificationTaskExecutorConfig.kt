package team.inreok.getiserver.domain.notification.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

/**
 * 인앱 알림 생성이 요청 Thread를 막지 않도록 별도 Thread Pool을 둔다(PR #128 리뷰 지적,
 * `docs/Notification/notification-core-plan.md` §14가 "PR 3 — 도메인 Event 연결"에 예고한
 * 전용 `TaskExecutor` 방향). [team.inreok.getiserver.domain.search.config.SearchTaskExecutorConfig]와
 * 동일한 이유·구조이며, 전역 `@Async`(Common Pool)를 쓰지 않고 이 Bean을 Listener가 직접
 * 주입받아 사용한다.
 *
 * `@TransactionalEventListener(AFTER_COMMIT)`은 원본 Transaction을 Commit하는 같은 호출 흐름에서
 * 동기 실행된다. 프로그램 삭제처럼 수신자가 여럿인 알림은 수신자 수만큼 Insert가 이어지므로,
 * 이 Executor로 넘기지 않으면 삭제 API 응답 시간이 신청자 수에 비례해 늘어난다
 * (`Program.capacity`가 nullable이라 상한도 없다).
 */
@Configuration
class NotificationTaskExecutorConfig {
    @Bean
    fun notificationTaskExecutor(): TaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = CORE_POOL_SIZE
            maxPoolSize = MAX_POOL_SIZE
            queueCapacity = QUEUE_CAPACITY
            setThreadNamePrefix("notification-exec-")
            initialize()
        }

    private companion object {
        const val CORE_POOL_SIZE = 1
        const val MAX_POOL_SIZE = 4
        const val QUEUE_CAPACITY = 500
    }
}
