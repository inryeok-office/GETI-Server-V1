package team.inreok.getiserver.domain.search.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

/**
 * 색인 동기화(`JobChangedEvent` 처리)와 전체 재색인이 요청·저장 Thread를 막지 않도록 별도
 * Thread Pool을 둔다(Issue #69, `CollectorTaskExecutorConfig`와 동일한 이유). 전역
 * `@Async`(Common Pool)를 쓰지 않고 이 Bean을 Search Component가 직접 주입받아 사용한다.
 */
@Configuration
class SearchTaskExecutorConfig {
    @Bean
    fun searchTaskExecutor(): TaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = CORE_POOL_SIZE
            maxPoolSize = MAX_POOL_SIZE
            queueCapacity = QUEUE_CAPACITY
            setThreadNamePrefix("search-exec-")
            initialize()
        }

    private companion object {
        const val CORE_POOL_SIZE = 1
        const val MAX_POOL_SIZE = 4
        const val QUEUE_CAPACITY = 500
    }
}
