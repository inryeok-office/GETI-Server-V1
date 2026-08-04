package team.inreok.getiserver.domain.search.scheduler

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.search.repository.SearchIndexFailureRepository
import team.inreok.getiserver.domain.search.service.JobIndexSyncService
import java.time.LocalDateTime

/**
 * 실패했던 색인 동기화를 주기적으로 재시도한다(Issue #69, `JobNotificationRetryScheduler`와
 * 동일한 패턴). 재시도 대상은 DB(`search_index_failures`)에 남아 있으므로 서버가 재시작돼도
 * 유실되지 않는다. `syncOne`이 다시 실패하면 같은 Row의 시도 횟수/다음 재시도 시각을 갱신하고,
 * 성공하면 Row를 지운다(`JobIndexSyncServiceImpl` 참고).
 */
@Component
class SearchIndexRetryScheduler(
    private val failureRepository: SearchIndexFailureRepository,
    private val jobIndexSyncService: JobIndexSyncService,
) {
    private val log = LoggerFactory.getLogger(SearchIndexRetryScheduler::class.java)

    @Scheduled(fixedDelayString = "\${app.search.index-retry-interval-ms:60000}")
    fun retryDueFailures() {
        val due = failureRepository.findDueForRetry(LocalDateTime.now())
        due.forEach { failure ->
            runCatching { jobIndexSyncService.syncOne(failure.jobId) }
                .onFailure { ex -> log.error("색인 재시도 중 처리되지 않은 오류(jobId={})", failure.jobId, ex) }
        }
    }
}
