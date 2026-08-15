package team.inreok.getiserver.domain.recommendation.service.impl

import team.inreok.getiserver.domain.ai.query.AiAnalysisSearchSnapshot
import team.inreok.getiserver.domain.recommendation.entity.type.RecommendationReasonType
import team.inreok.getiserver.domain.recommendation.entity.type.RecommendationReasonType.ENTRY_LEVEL_SUITABLE
import team.inreok.getiserver.domain.recommendation.entity.type.RecommendationReasonType.HIGH_SCHOOL_SUITABLE
import team.inreok.getiserver.domain.recommendation.entity.type.RecommendationReasonType.PREFERRED_SKILL_MATCH
import team.inreok.getiserver.domain.recommendation.entity.type.RecommendationReasonType.REQUIRED_SKILL_MATCH
import team.inreok.getiserver.domain.recommendation.entity.type.SuitabilityLevel
import kotlin.math.roundToInt

/**
 * Recommendation Score 계산식의 Version이다(R1 설계 §27, §33 "algorithmVersion"). Weight/변환
 * 방식이 바뀌면 이 값을 올리고, 이전 값의 의미를 코드에서 바꾸지 않는다.
 */
const val RECOMMENDATION_ALGORITHM_VERSION = 1

// R1 설계 §7/§13 Weight 초안. 다섯 Factor 합이 100이다.
private const val REQUIRED_SKILL_WEIGHT = 50.0
private const val PREFERRED_SKILL_WEIGHT = 20.0
private const val HIGH_SCHOOL_FIT_WEIGHT = 15.0
private const val ENTRY_LEVEL_FIT_WEIGHT = 10.0
private const val DIFFICULTY_WEIGHT = 5.0

private const val FIT_SUITABLE = 1.0
private const val FIT_CONDITIONAL = 0.5
private const val FIT_UNSUITABLE = 0.0

private const val DIFFICULTY_EASY = 1.0
private const val DIFFICULTY_NORMAL = 0.7
private const val DIFFICULTY_HARD = 0.4

// R1 설계 §8 Score 구간 -> SuitabilityLevel 변환 경계값.
private const val HIGHLY_RECOMMENDED_MIN_SCORE = 80
private const val RECOMMENDED_MIN_SCORE = 60
private const val NORMAL_MIN_SCORE = 40
private const val UNSUITABLE_MIN_SCORE = 20

/** 구조화된 추천 이유 하나다(R2 설계 §23). 자연어 문장은 담지 않는다. */
data class RecommendationReason(
    val type: RecommendationReasonType,
    val matchedCount: Int? = null,
    val totalCount: Int? = null,
)

/** Score Engine의 계산 결과다. */
data class RecommendationScoreResult(
    val score: Int,
    val reasons: List<RecommendationReason>,
)

/** 계산에 쓸 수 있는 값이 있는 Factor 하나를 나타낸다. */
private data class ScorableFactor(
    val weight: Double,
    val ratio: Double,
)

/**
 * Recommendation Score Engine이다(R2 요구사항, R1 설계 §7/§17/§20~§21). Repository/Port/Spring
 * Bean 의존이 전혀 없는 순수 함수로 둬 모든 Weight/Rebalancing 분기를 Unit Test로 직접 검증할 수
 * 있게 한다.
 *
 * `aiSnapshot`이 null이면(AI 분석이 COMPLETED가 아님) 다섯 Factor가 모두 unavailable이라
 * `null`을 반환한다(추천 근거가 전혀 없는 공고는 후보에서 빠진다, R1 설계 §45). 그 외에는
 * requiredSkill/preferredSkill이 비어 있어도(=unavailable) 나머지 Factor만으로 Weight를
 * 재정규화해 계산한다(R1 설계 §17 Missing Factor Rebalancing).
 */
@Suppress("ReturnCount")
fun calculateScore(
    memberTechStackIds: Set<Long>,
    aiSnapshot: AiAnalysisSearchSnapshot?,
): RecommendationScoreResult? {
    if (aiSnapshot == null) return null

    val required = matchSkills(memberTechStackIds, aiSnapshot.requiredTechStackIds.toSet())
    // required와 preferred에 동시에 존재하는 비정상 데이터는 required를 우선하고 preferred
    // 집합에서 제거한다(R2 요구사항 §15, 이중 가중치 방지).
    val preferredIds = aiSnapshot.preferredTechStackIds.toSet() - aiSnapshot.requiredTechStackIds.toSet()
    val preferred = matchSkills(memberTechStackIds, preferredIds)

    val highSchoolFit = fitScoreOf(aiSnapshot.highSchoolGraduateFit)
    val entryLevelFit = fitScoreOf(aiSnapshot.entryLevelFit)
    val difficulty = difficultyScoreOf(aiSnapshot.difficulty)

    val factors =
        listOfNotNull(
            required?.let { ScorableFactor(REQUIRED_SKILL_WEIGHT, it.ratio) },
            preferred?.let { ScorableFactor(PREFERRED_SKILL_WEIGHT, it.ratio) },
            highSchoolFit?.let { ScorableFactor(HIGH_SCHOOL_FIT_WEIGHT, it) },
            entryLevelFit?.let { ScorableFactor(ENTRY_LEVEL_FIT_WEIGHT, it) },
            difficulty?.let { ScorableFactor(DIFFICULTY_WEIGHT, it) },
        )
    val availableWeightSum = factors.sumOf { it.weight }
    // 모든 Factor가 unavailable이면(이론상 AI 분석이 COMPLETED인데 다섯 값이 전부 비어 있는
    // 경우) 추천 근거가 없으므로 후보에서 제외한다(R2 요구사항 §18 NO_SCORABLE_FACTORS).
    if (availableWeightSum == 0.0) return null

    val rawWeightedSum = factors.sumOf { it.weight * it.ratio }
    val score = (rawWeightedSum / availableWeightSum * 100).roundToInt().coerceIn(0, 100)

    return RecommendationScoreResult(
        score = score,
        reasons = buildReasons(required, preferred, aiSnapshot),
    )
}

/** Score 구간을 기존 Skeleton의 [SuitabilityLevel] 5단계로 변환한다(R1 설계 §8). */
fun suitabilityOf(score: Int): SuitabilityLevel =
    when {
        score >= HIGHLY_RECOMMENDED_MIN_SCORE -> SuitabilityLevel.HIGHLY_RECOMMENDED
        score >= RECOMMENDED_MIN_SCORE -> SuitabilityLevel.RECOMMENDED
        score >= NORMAL_MIN_SCORE -> SuitabilityLevel.NORMAL
        score >= UNSUITABLE_MIN_SCORE -> SuitabilityLevel.UNSUITABLE
        else -> SuitabilityLevel.VERY_UNSUITABLE
    }

private data class SkillMatch(
    val matchedCount: Int,
    val totalCount: Int,
) {
    val ratio: Double get() = matchedCount.toDouble() / totalCount
}

/** `techStackIds`가 비어 있으면(=AI가 추출하지 못했거나 공고에 명시가 없음) unavailable(null)이다. */
private fun matchSkills(
    memberTechStackIds: Set<Long>,
    techStackIds: Set<Long>,
): SkillMatch? {
    if (techStackIds.isEmpty()) return null
    val matched = (memberTechStackIds intersect techStackIds).size
    return SkillMatch(matchedCount = matched, totalCount = techStackIds.size)
}

private fun fitScoreOf(fitLevel: String?): Double? =
    when (fitLevel) {
        "SUITABLE" -> FIT_SUITABLE
        "CONDITIONAL" -> FIT_CONDITIONAL
        "UNSUITABLE" -> FIT_UNSUITABLE
        else -> null
    }

private fun difficultyScoreOf(difficulty: String?): Double? =
    when (difficulty) {
        "EASY" -> DIFFICULTY_EASY
        "NORMAL" -> DIFFICULTY_NORMAL
        "HARD" -> DIFFICULTY_HARD
        else -> null
    }

/**
 * 학생에게 실제로 의미 있는 상위 요소만 Reason으로 남긴다(R1 설계 §24). difficulty는 Score에는
 * 반영되지만 Reason에는 넣지 않는다.
 */
private fun buildReasons(
    required: SkillMatch?,
    preferred: SkillMatch?,
    aiSnapshot: AiAnalysisSearchSnapshot,
): List<RecommendationReason> =
    listOfNotNull(
        required
            ?.takeIf { it.matchedCount > 0 }
            ?.let { RecommendationReason(REQUIRED_SKILL_MATCH, it.matchedCount, it.totalCount) },
        preferred
            ?.takeIf { it.matchedCount > 0 }
            ?.let { RecommendationReason(PREFERRED_SKILL_MATCH, it.matchedCount, it.totalCount) },
        RecommendationReason(HIGH_SCHOOL_SUITABLE).takeIf { aiSnapshot.highSchoolGraduateFit == "SUITABLE" },
        RecommendationReason(ENTRY_LEVEL_SUITABLE).takeIf { aiSnapshot.entryLevelFit == "SUITABLE" },
    )
