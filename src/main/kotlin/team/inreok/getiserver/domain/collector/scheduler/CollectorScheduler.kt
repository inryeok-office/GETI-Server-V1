package team.inreok.getiserver.domain.collector.scheduler

import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.collector.service.CollectorExecutionService

/**
 * 일일 수집 진입점이다. Scheduler는 실행 시각 결정과 Use Case 호출만 담당하고 Provider 호출이나
 * Repository 접근은 직접 하지 않는다(`CollectorExecutionService`에 위임). 실행 시각은
 * `app.collector.scheduler.cron`으로 외부화한다.
 *
 * `COLLECTOR_EXTERNAL_SCHEDULER_ENABLED`(기본 false)가 꺼져 있으면 실행하지 않는다 — 개발 서버
 * 배포에서 실제 Provider 인증키가 없거나 자동 외부 호출을 원하지 않을 때 안전하게 끌 수 있게
 * 한다. Scheduler가 꺼져 있어도 수동 실행 API(`POST /api/v1/admin/collector-actions`)와
 * `CollectorExecutionService` 직접 Test는 영향을 받지 않는다.
 *
 * 여러 Instance가 동시에 떠 있으면 이 Method가 중복 실행될 수 있다. ShedLock 같은 분산 Lock은
 * 이번 범위에서 추가하지 않았다(현재 서버 한 대 배포 기준, 최종 보고 참고) — 대신
 * `CollectorExecutionService`가 Source 단위로 이미 진행 중인 실행(PENDING/RUNNING CollectionRun)이
 * 있으면 Skip하므로 중복 실행 자체는 방지된다.
 */
@Component
class CollectorScheduler(
    private val collectorExecutionService: CollectorExecutionService,
    @param:Value("\${app.collector.scheduler.external-enabled:false}") private val externalSchedulerEnabled: Boolean,
) {
    @Scheduled(cron = "\${app.collector.scheduler.cron:0 0 3 * * *}")
    fun runDailyCollection() {
        if (!externalSchedulerEnabled) return
        collectorExecutionService.runDailyCollection()
    }
}
