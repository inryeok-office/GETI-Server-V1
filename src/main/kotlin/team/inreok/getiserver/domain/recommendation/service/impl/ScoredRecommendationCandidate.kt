package team.inreok.getiserver.domain.recommendation.service.impl

import team.inreok.getiserver.domain.job.query.JobRecommendationCandidateSnapshot

/** Score까지 계산된 후보 하나다(Ranking 입력). */
data class ScoredRecommendationCandidate(
    val job: JobRecommendationCandidateSnapshot,
    val result: RecommendationScoreResult,
)
