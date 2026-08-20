package team.inreok.getiserver.domain.recommendation.service.impl

import team.inreok.getiserver.domain.ai.query.AiAnalysisSearchSnapshot

/*
 * SIMILAR_JOBS Hard Filter가 두 공고를 "유사"로 볼지 판정하는 순수 함수다(R6, Issue #167).
 * `RecommendationScoringEngine`(회원 ↔ 공고 기술 매칭 Score)과는 목적이 달라(공고 ↔ 공고)
 * 재사용하거나 변경하지 않고, SIMILAR_JOBS 전용으로 별도로 둔다. Repository/Port 호출이 없어
 * 모든 분기를 Unit Test에서 직접 검증할 수 있다(`RecommendationCandidateFilter`와 같은 이유).
 *
 * Formula(Issue #167 DECISION_REQUIRED 확정, Option A): 두 Job 각각의
 * `requiredTechStackIds ∪ preferredTechStackIds`를 하나의 기술 스택 Set으로 만들고 Jaccard
 * Similarity(`|교집합| / |합집합|`)를 계산한다. required/preferred에 별도 가중치를 두지 않는다.
 * `companyId`/`postingType`/`applicationMethod`/`difficulty`는 이번 범위에 포함하지 않는다(같은
 * 회사라는 이유만으로 다른 직무까지 숨기는 것을 방지, Issue #167 §3).
 *
 * SIMILAR_JOBS를 등록하는 Generation(`RecommendationGenerationServiceImpl`)과 등록 즉시 반영
 * (`RecommendationServiceImpl.registerExclusion`) 두 호출부가 서로 다른 Formula/Threshold를
 * 구현하지 않도록 이 함수들을 공통으로 재사용한다.
 */

/** Job의 `required ∪ preferred` 기술 스택 Set을 만든다. 매칭된 기술이 하나도 없으면 빈 Set이다. */
fun techStackSetOf(snapshot: AiAnalysisSearchSnapshot): Set<Long> =
    (snapshot.requiredTechStackIds + snapshot.preferredTechStackIds).toSet()

/**
 * 두 기술 스택 Set의 Jaccard Similarity를 계산한다. 둘 중 하나라도 비어 있으면(AI 분석은 있지만
 * 매칭된 기술이 하나도 없음) 의미 있는 비교가 불가능하므로 `null`을 반환한다 -- 0/0을 1.0 등으로
 * 처리하지 않는다(Issue #167 §4).
 */
fun computeJaccardSimilarity(
    sourceTechStackIds: Set<Long>,
    candidateTechStackIds: Set<Long>,
): Double? {
    if (sourceTechStackIds.isEmpty() || candidateTechStackIds.isEmpty()) return null
    val intersectionSize = sourceTechStackIds.intersect(candidateTechStackIds).size
    val unionSize = sourceTechStackIds.union(candidateTechStackIds).size
    return intersectionSize.toDouble() / unionSize
}

/** [computeJaccardSimilarity]가 `threshold` 이상이면 SIMILAR로 판정한다. `null`(비교 불가)은 항상 false다. */
fun isSimilarJob(
    sourceTechStackIds: Set<Long>,
    candidateTechStackIds: Set<Long>,
    threshold: Double,
): Boolean {
    val similarity = computeJaccardSimilarity(sourceTechStackIds, candidateTechStackIds) ?: return false
    return similarity >= threshold
}

/**
 * 후보 Job이 여러 SIMILAR_JOBS 기준 Job([sourceTechStackIdsList]) 중 하나라도 유사하면 true다
 * (Issue #167 §5 "여러 Source 중 하나라도 0.6 이상이면 제외"). [candidateAiSnapshot]이 없으면(AI
 * 분석이 없거나 COMPLETED가 아님) 비교 자체를 하지 않는다 -- Candidate AI 없음은 기존
 * `calculateScore` Flow가 이미 처리한다.
 */
fun isSimilarToAnySource(
    candidateAiSnapshot: AiAnalysisSearchSnapshot?,
    sourceTechStackIdsList: Collection<Set<Long>>,
    threshold: Double,
): Boolean {
    if (candidateAiSnapshot == null || sourceTechStackIdsList.isEmpty()) return false
    val candidateTechStackIds = techStackSetOf(candidateAiSnapshot)
    return sourceTechStackIdsList.any { sourceTechStackIds ->
        isSimilarJob(sourceTechStackIds, candidateTechStackIds, threshold)
    }
}
