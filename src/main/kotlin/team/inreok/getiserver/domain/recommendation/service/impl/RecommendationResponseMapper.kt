package team.inreok.getiserver.domain.recommendation.service.impl

import org.slf4j.Logger
import team.inreok.getiserver.domain.company.query.CompanySummary
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.query.JobRecommendationCandidateSnapshot
import team.inreok.getiserver.domain.recommendation.dto.RecommendationJobResponse
import team.inreok.getiserver.domain.recommendation.dto.RecommendationReasonResponse
import team.inreok.getiserver.domain.recommendation.entity.Recommendation
import team.inreok.getiserver.domain.recommendation.service.RecommendationReason
import tools.jackson.databind.ObjectMapper

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
