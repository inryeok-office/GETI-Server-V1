package team.inreok.getiserver.domain.collector.scheduler

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

/**
 * `POST /api/v1/admin/collector-actions`가 202 Accepted를 반환하려면 요청 Thread가 외부 수집
 * 완료를 기다리지 않고 즉시 반환해야 한다(Issue #62). 현재 서버 한 대 배포를 기준으로 하므로
 * Kafka나 별도 Worker 대신 제한된 크기의 Thread Pool만 둔다. 무제한 Thread 생성이나 Common
 * Pool 의존을 피하기 위해 `@Async`(전역 `@EnableAsync`)를 쓰지 않고 이 Bean을 Collector Service가
 * 직접 주입받아 사용한다.
 */
@Configuration
class CollectorTaskExecutorConfig {
    @Bean
    fun collectorTaskExecutor(): TaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = CORE_POOL_SIZE
            maxPoolSize = MAX_POOL_SIZE
            queueCapacity = QUEUE_CAPACITY
            setThreadNamePrefix("collector-exec-")
            initialize()
        }

    private companion object {
        const val CORE_POOL_SIZE = 1
        const val MAX_POOL_SIZE = 4
        const val QUEUE_CAPACITY = 100
    }
}
