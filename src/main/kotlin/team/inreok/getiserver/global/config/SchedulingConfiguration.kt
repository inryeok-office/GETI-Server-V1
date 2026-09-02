package team.inreok.getiserver.global.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * 운영에서는 기본적으로 모든 `@Scheduled` 작업을 활성화한다. Integration Test에서는
 * [ConditionalOnProperty]를 통해 Scheduling 인프라만 끄고 Scheduler Bean 자체는 유지한다.
 * 따라서 Test가 Scheduler Method를 직접 호출하는 계약은 보존하면서, 백그라운드 작업이 Test
 * 데이터와 경합하는 것을 막는다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
    name = ["app.scheduling.enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class SchedulingConfiguration
