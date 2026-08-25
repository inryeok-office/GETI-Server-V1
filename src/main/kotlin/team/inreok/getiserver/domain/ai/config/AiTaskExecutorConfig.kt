package team.inreok.getiserver.domain.ai.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

/**
 * AI 분석(OpenAI 호출)이 요청·저장 Thread를 막지 않도록 별도 Thread Pool을 둔다
 * (`SearchTaskExecutorConfig`와 같은 이유). Job 게시 API·수동 재분석 API 모두 이 Executor로
 * 실제 분석을 넘기고 즉시 응답한다.
 */
@Configuration
class AiTaskExecutorConfig {
    @Bean
    fun aiTaskExecutor(): TaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = CORE_POOL_SIZE
            maxPoolSize = MAX_POOL_SIZE
            queueCapacity = QUEUE_CAPACITY
            setThreadNamePrefix("ai-exec-")
            initialize()
        }

    private companion object {
        const val CORE_POOL_SIZE = 1
        const val MAX_POOL_SIZE = 4
        const val QUEUE_CAPACITY = 500
    }
}
