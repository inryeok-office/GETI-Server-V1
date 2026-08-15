package team.inreok.getiserver.domain.ai.event

import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import team.inreok.getiserver.domain.ai.service.AiAnalysisService

/**
 * [AiAnalysisRequestedEvent]가 활성 DB Transaction 없이 발행돼도 실제로 Worker를 깨우는지
 * Spring [org.springframework.context.ApplicationEventPublisher]를 통해 검증한다.
 *
 * 이 Class가 존재하는 이유: `AiAnalysisRequestedEvent`는 항상 `AiAnalysisTransitionService`가
 * 이미 Commit을 마친 *뒤*(활성 Transaction이 없는 상태)에 발행된다(`AiAnalysisServiceImpl`
 * Self-Invocation 회피 설계 참고). `@TransactionalEventListener(AFTER_COMMIT)`로
 * [AiAnalysisWorkEventListener]를 구현했던 초기 버전은 발행 시점에 걸어 줄 Transaction 자체가
 * 없어 Event가 조용히 버려지는 실제 운영 결함이 있었다(Live E2E 검증에서 발견, Issue #132) --
 * `AiAnalysisTransitionServiceImplTest` 같은 Mockito 단위 Test는 Method를 직접 호출하므로 이
 * 종류의 결함(Spring Event Dispatch 자체의 문제)을 잡지 못한다. 이 Test는 실제
 * `ApplicationEventPublisher.publishEvent`를 거쳐야만 의미가 있다.
 */
class AiAnalysisRequestedEventDispatchTest {
    @Test
    fun `활성 Transaction이 없어도 Event 발행만으로 Worker가 호출된다`() {
        val aiAnalysisService = mock(AiAnalysisService::class.java)
        val context = AnnotationConfigApplicationContext()
        context.beanFactory.registerSingleton("aiAnalysisService", aiAnalysisService)
        context.register(TestConfig::class.java, AiAnalysisWorkEventListener::class.java)
        context.refresh()

        try {
            // 활성 DB Transaction이 전혀 없는 일반 Thread에서 발행한다 -- Production에서
            // AiAnalysisServiceImpl.triggerInitialAnalysisIfAbsent/reanalyze가 발행하는 상황과
            // 같다.
            context.publishEvent(AiAnalysisRequestedEvent(1L))

            verify(aiAnalysisService, timeout(2_000)).processAnalysis(1L)
        } finally {
            context.close()
        }
    }

    @Configuration
    private open class TestConfig {
        @Bean
        open fun aiTaskExecutor(): TaskExecutor =
            ThreadPoolTaskExecutor().apply {
                corePoolSize = 1
                maxPoolSize = 1
                setThreadNamePrefix("ai-exec-test-")
                initialize()
            }
    }
}
