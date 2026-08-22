package team.inreok.getiserver.domain.system.service.impl

import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Pageable
import org.springframework.scheduling.support.CronExpression
import org.springframework.stereotype.Service
import team.inreok.getiserver.domain.collector.notification.query.JobNotificationSchedulerStatusQueryPort
import team.inreok.getiserver.domain.collector.query.CollectorSchedulerStatusQueryPort
import team.inreok.getiserver.domain.notification.query.DiscordDeliverySchedulerStatusQueryPort
import team.inreok.getiserver.domain.operation.entity.type.OperationJobType
import team.inreok.getiserver.domain.operation.query.OperationJobState
import team.inreok.getiserver.domain.program.query.ProgramCloseSchedulerStatusQueryPort
import team.inreok.getiserver.domain.recommendation.query.RecommendationSchedulerStatusQueryPort
import team.inreok.getiserver.domain.search.query.SearchIndexSchedulerStatusQueryPort
import team.inreok.getiserver.domain.system.dto.OperationJobActionStatus
import team.inreok.getiserver.domain.system.dto.OperationJobListResponse
import team.inreok.getiserver.domain.system.dto.OperationJobResponse
import team.inreok.getiserver.domain.system.service.OperationJobQueryService
import java.time.LocalDateTime

@Service
class OperationJobQueryServiceImpl(
    private val collectorStatusPort: CollectorSchedulerStatusQueryPort,
    private val jobNotificationStatusPort: JobNotificationSchedulerStatusQueryPort,
    private val discordDeliveryStatusPort: DiscordDeliverySchedulerStatusQueryPort,
    private val programCloseStatusPort: ProgramCloseSchedulerStatusQueryPort,
    private val recommendationStatusPort: RecommendationSchedulerStatusQueryPort,
    private val searchIndexStatusPort: SearchIndexSchedulerStatusQueryPort,
    @param:Value("\${app.collector.scheduler.cron:0 0 3 * * *}") private val collectorCron: String,
    @param:Value("\${app.discord.job-notification.retry-interval-ms:300000}") private val jobNotificationDelayMs: Long,
    @param:Value("\${app.discord.bot.sweep-interval-ms:60000}") private val discordDeliveryDelayMs: Long,
    @param:Value("\${app.program.close-scheduler.interval-ms:60000}") private val programCloseDelayMs: Long,
    @param:Value("\${app.recommendation.generation-scheduler.cron:-}") private val recommendationCron: String,
    @param:Value("\${app.search.index-retry-interval-ms:60000}") private val searchIndexDelayMs: Long,
) : OperationJobQueryService {
    override fun search(
        jobType: OperationJobType?,
        status: String?,
        startAt: LocalDateTime?,
        endAt: LocalDateTime?,
        pageable: Pageable,
    ): OperationJobListResponse {
        val now = LocalDateTime.now()
        val jobs =
            definitions(now)
                .filter { jobType == null || it.jobType == jobType }
                .filter { status == null || it.status == status }
                .filter { startAt == null || it.startedAt?.let { value -> !value.isBefore(startAt) } == true }
                .filter { endAt == null || it.startedAt?.let { value -> !value.isAfter(endAt) } == true }
        val fromIndex = (pageable.offset).toInt().coerceAtMost(jobs.size)
        val toIndex = (fromIndex + pageable.pageSize).coerceAtMost(jobs.size)
        val content = jobs.subList(fromIndex, toIndex)
        val totalPages = if (jobs.isEmpty()) 0 else (jobs.size + pageable.pageSize - 1) / pageable.pageSize
        return OperationJobListResponse(
            content = content,
            page = pageable.pageNumber,
            size = pageable.pageSize,
            totalElements = jobs.size.toLong(),
            totalPages = totalPages,
            first = pageable.pageNumber == 0,
            last = toIndex == jobs.size,
        )
    }

    private fun definitions(now: LocalDateTime): List<OperationJobResponse> =
        listOf(
            job(
                jobType = OperationJobType.JOB_COLLECTION,
                name = "채용 공고 수집",
                description = "외부 채용 공고를 수집합니다.",
                schedule = "cron: $collectorCron",
                state = collectorStatusPort.getStatus(),
                actionStatus = OperationJobActionStatus.SUPPORTED,
                nextRunAt = nextCronAt(collectorCron, now),
            ),
            job(
                jobType = OperationJobType.JOB_NOTIFICATION_RETRY,
                name = "공고 Discord 알림 재시도",
                description = "실패한 공고 Discord 알림을 재시도합니다.",
                schedule = "fixedDelay: ${jobNotificationDelayMs}ms",
                state = jobNotificationStatusPort.getStatus(),
                actionStatus = OperationJobActionStatus.UNSUPPORTED,
                nextRunAt = null,
            ),
            job(
                jobType = OperationJobType.DISCORD_DELIVERY_RETRY,
                name = "Discord 전달 재시도",
                description = "처리할 Discord 전달을 주기적으로 전송합니다.",
                schedule = "fixedDelay: ${discordDeliveryDelayMs}ms",
                state = discordDeliveryStatusPort.getStatus(),
                actionStatus = OperationJobActionStatus.SUPPORTED,
                nextRunAt = null,
            ),
            job(
                jobType = OperationJobType.PROGRAM_CLOSE,
                name = "프로그램 자동 마감",
                description = "신청 종료 시각이 지난 프로그램을 자동 마감합니다.",
                schedule = "fixedDelay: ${programCloseDelayMs}ms",
                state = programCloseStatusPort.getStatus(),
                actionStatus = OperationJobActionStatus.UNSUPPORTED,
                nextRunAt = null,
            ),
            job(
                jobType = OperationJobType.RECOMMENDATION_GENERATION,
                name = "일일 추천 생성",
                description = "추천을 활성화한 학생의 추천을 생성합니다.",
                schedule = "cron: $recommendationCron",
                state = recommendationStatusPort.getStatus(),
                actionStatus = OperationJobActionStatus.UNSUPPORTED,
                nextRunAt = nextCronAt(recommendationCron, now),
            ),
            job(
                jobType = OperationJobType.SEARCH_INDEX_RETRY,
                name = "검색 색인 재시도",
                description = "실패한 검색 색인 동기화를 재시도합니다.",
                schedule = "fixedDelay: ${searchIndexDelayMs}ms",
                state = searchIndexStatusPort.getStatus(),
                actionStatus = OperationJobActionStatus.UNSUPPORTED,
                nextRunAt = null,
            ),
        )

    private fun job(
        jobType: OperationJobType,
        name: String,
        description: String,
        schedule: String,
        state: OperationJobState,
        actionStatus: OperationJobActionStatus,
        nextRunAt: LocalDateTime?,
    ) = OperationJobResponse(
        taskId = jobType,
        jobType = jobType,
        name = name,
        description = description,
        schedule = schedule,
        lastRunAt = state.lastRunAt,
        nextRunAt = nextRunAt,
        operationId = state.operationId,
        status = state.status,
        processedCount = state.processedCount,
        successCount = state.successCount,
        failureCount = state.failureCount,
        partialSuccessCount = state.partialSuccessCount,
        startedAt = state.startedAt,
        finishedAt = state.finishedAt,
        lastError = state.lastError,
        actionStatus = actionStatus,
    )

    private fun nextCronAt(
        cron: String,
        now: LocalDateTime,
    ): LocalDateTime? {
        if (cron == SCHEDULER_DISABLED_CRON) return null
        return CronExpression.parse(cron).next(now)
    }

    private companion object {
        const val SCHEDULER_DISABLED_CRON = "-"
    }
}
