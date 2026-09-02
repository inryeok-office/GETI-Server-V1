package team.inreok.getiserver.domain.recommendation.service.impl

import org.slf4j.Logger
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.scheduling.support.CronExpression
import team.inreok.getiserver.domain.company.query.CompanySummary
import team.inreok.getiserver.domain.job.access.JobAiSkillAccessView
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.query.JobRecommendationCandidateSnapshot
import team.inreok.getiserver.domain.recommendation.dto.RecommendationJobResponse
import team.inreok.getiserver.domain.recommendation.dto.RecommendationListResponse
import team.inreok.getiserver.domain.recommendation.dto.RecommendationReasonResponse
import team.inreok.getiserver.domain.recommendation.dto.RecommendationStatus
import team.inreok.getiserver.domain.recommendation.entity.Recommendation
import team.inreok.getiserver.domain.recommendation.service.RecommendationReason
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime

/**
 * [RecommendationServiceImpl]의 응답 조립 Helper다. 상태를 갖지 않는 순수 변환 로직이라
 * 별도 File의 최상위 함수로 분리했다 -- Class 안에 두면 detekt `TooManyFunctions`(허용 11개)를
 * 넘어선다.
 *
 * `job`/`company` Entity를 직접 참조하지 않고 `job.query.JobRecommendationCandidateQueryPort`/
 * `company.query.CompanyQuery`가 돌려준 값만 옮겨 담는다(Recommendation R3 Issue #152, Notion
 * 계약 정합성 Issue #155).
 */
internal fun buildRecommendationJobResponse(
    job: JobRecommendationCandidateSnapshot,
    company: CompanySummary?,
    bookmarked: Boolean,
    techStacks: List<JobAiSkillAccessView> = emptyList(),
    bookmarkCount: Long = 0L,
) = RecommendationJobResponse(
    jobId = job.jobId,
    title = job.title,
    postingType = job.postingType,
    applicationMethod = job.applicationMethod,
    status = JobStatus.valueOf(job.status),
    company = company,
    endDate = job.recruitmentEndedAt,
    viewCount = job.viewCount,
    bookmarked = bookmarked,
    techStacks = techStacks,
    bookmarkCount = bookmarkCount,
    location = job.location,
    employmentType = job.employmentType,
)

/**
 * `reasons` JSON 파싱 실패는 이 Row 하나만 이유 없이 보여주고 나머지 목록은 그대로 반환한다 --
 * 저장된 Reason 하나가 손상됐다고 전체 추천 조회가 500이 되면 안 된다(요구사항 "파싱 실패 시
 * 전체 Recommendation 조회가 500이 되는 구조 피하기"). 다만 조용히 숨기지 않고 원인을 추적할 수
 * 있도록 warn Log를 남긴다.
 */
internal fun parseRecommendationReasons(
    row: Recommendation,
    objectMapper: ObjectMapper,
    log: Logger,
): List<RecommendationReasonResponse> {
    val json = row.reasons ?: return emptyList()
    return runCatching {
        objectMapper
            .readValue(json, Array<RecommendationReason>::class.java)
            .map { RecommendationReasonResponse(it.type, it.matchedCount, it.totalCount) }
    }.getOrElse { ex ->
        log.warn(
            "Recommendation reasons 파싱 실패(recommendationId={}, memberId={}, jobId={})",
            row.id,
            row.memberId,
            row.jobId,
            ex,
        )
        emptyList()
    }
}

/** 추천 기능이 꺼진 회원에게 돌려주는 고정 응답이다(Recommendation R3, Issue #152). */
internal fun disabledRecommendationListResponse(pageable: Pageable): RecommendationListResponse =
    RecommendationListResponse.of(
        enabled = false,
        status = RecommendationStatus.DISABLED,
        generatedAt = null,
        nextGenerationAt = null,
        page = Page.empty(pageable),
    )

/**
 * GENERATING/FAILED 상태인 회원에게 돌려주는 응답이다(Recommendation R4, Issue #160). 두 상태 모두
 * 오늘자 Recommendation Row가 없거나(계산 시작 전) 신뢰할 수 없어(계산 실패, Transaction 자체가
 * Rollback됨) `content`/`generatedAt`을 항상 비운다.
 */
internal fun inProgressRecommendationListResponse(
    pageable: Pageable,
    status: RecommendationStatus,
    nextGenerationAt: LocalDateTime?,
): RecommendationListResponse =
    RecommendationListResponse.of(
        enabled = true,
        status = status,
        generatedAt = null,
        nextGenerationAt = nextGenerationAt,
        page = Page.empty(pageable),
    )

/**
 * Daily Scheduler의 [cron] 설정 기준으로 다음 생성 예정 시각을 계산한다(Recommendation R4, Issue
 * #160). [cron]이 [SCHEDULER_DISABLED_CRON](Spring `@Scheduled`가 지원하는 비활성 Sentinel)이면
 * Scheduler 자체가 등록되지 않으므로 null을 반환한다(임의 값을 채우지 않는다, DTO 문서 참고).
 */
internal fun nextGenerationAt(cron: String): LocalDateTime? {
    if (cron == SCHEDULER_DISABLED_CRON) return null
    return CronExpression.parse(cron).next(LocalDateTime.now())
}

/**
 * Spring `@Scheduled`의 cron 비활성 Sentinel이다(Reference Manual "Cron Expressions" 절 참고).
 * `RecommendationGenerationScheduler`/`application.yaml`의 기본값과 항상 같은 값이어야 하는 계약이라
 * 이 값이 바뀌면 세 곳(이 상수, Scheduler의 `@Scheduled` 기본값, application.yaml 기본값)을 함께
 * 바꿔야 한다.
 */
internal const val SCHEDULER_DISABLED_CRON = "-"
