package team.inreok.getiserver.domain.recommendation.service.impl

import team.inreok.getiserver.domain.recommendation.service.RankedRecommendation
import java.time.LocalDateTime

/**
 * Recommendation Ranking이다(R2 요구사항, R1 설계 §29). score DESC -> publishedAt DESC ->
 * jobId DESC로 완전히 결정적인 순서를 만든다 -- 동일 입력이면 항상 동일 순서/rank가 나온다
 * (Random 사용 금지).
 *
 * `limit`은 R2 Core Engine의 Parameter일 뿐 운영 Top N을 여기서 고정하지 않는다(R1 설계 §30,
 * 실제 운영 값은 R4 Scheduler 설정에서 결정).
 */
fun rank(
    candidates: List<ScoredRecommendationCandidate>,
    limit: Int,
): List<RankedRecommendation> =
    candidates
        .sortedWith(
            compareByDescending<ScoredRecommendationCandidate> { it.result.score }
                .thenByDescending { it.job.publishedAt ?: LocalDateTime.MIN }
                .thenByDescending { it.job.jobId },
        ).take(limit)
        .mapIndexed { index, candidate ->
            RankedRecommendation(
                jobId = candidate.job.jobId,
                score = candidate.result.score,
                reasons = candidate.result.reasons,
                rank = index + 1,
            )
        }
