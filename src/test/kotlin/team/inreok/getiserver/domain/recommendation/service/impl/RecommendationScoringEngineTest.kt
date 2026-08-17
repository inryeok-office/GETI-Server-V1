package team.inreok.getiserver.domain.recommendation.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import team.inreok.getiserver.domain.ai.query.AiAnalysisSearchSnapshot
import team.inreok.getiserver.domain.recommendation.entity.type.RecommendationReasonType.ENTRY_LEVEL_SUITABLE
import team.inreok.getiserver.domain.recommendation.entity.type.RecommendationReasonType.HIGH_SCHOOL_SUITABLE
import team.inreok.getiserver.domain.recommendation.entity.type.RecommendationReasonType.PREFERRED_SKILL_MATCH
import team.inreok.getiserver.domain.recommendation.entity.type.RecommendationReasonType.REQUIRED_SKILL_MATCH
import team.inreok.getiserver.domain.recommendation.entity.type.SuitabilityLevel
import team.inreok.getiserver.domain.recommendation.service.RecommendationReason

class RecommendationScoringEngineTest {
    private fun snapshotOf(
        requiredTechStackIds: List<Long> = emptyList(),
        preferredTechStackIds: List<Long> = emptyList(),
        highSchoolGraduateFit: String? = null,
        entryLevelFit: String? = null,
        difficulty: String? = null,
    ) = AiAnalysisSearchSnapshot(
        requiredTechStackIds,
        preferredTechStackIds,
        highSchoolGraduateFit,
        entryLevelFit,
        difficulty,
    )

    @Test
    fun `AI 분석이 없으면(null) Score를 계산하지 않는다`() {
        val result = calculateScore(memberTechStackIds = setOf(1L), aiSnapshot = null)

        assertThat(result).isNull()
    }

    @Test
    fun `모든 Factor가 unavailable이면 NO_SCORABLE_FACTORS로 제외한다(null 반환)`() {
        val result =
            calculateScore(
                memberTechStackIds = setOf(1L),
                aiSnapshot = snapshotOf(),
            )

        assertThat(result).isNull()
    }

    @Test
    fun `필수 기술 전부 일치 + 적합도 SUITABLE이면 만점에 가깝다`() {
        val result =
            calculateScore(
                memberTechStackIds = setOf(1L, 2L, 3L),
                aiSnapshot =
                    snapshotOf(
                        requiredTechStackIds = listOf(1L, 2L, 3L),
                        highSchoolGraduateFit = "SUITABLE",
                        entryLevelFit = "SUITABLE",
                        difficulty = "EASY",
                    ),
            )

        assertThat(result).isNotNull
        assertThat(result!!.score).isEqualTo(100)
    }

    @Test
    fun `필수 기술 부분 일치는 비율만큼 점수가 낮아진다`() {
        val result =
            calculateScore(
                memberTechStackIds = setOf(1L, 3L, 7L),
                aiSnapshot = snapshotOf(requiredTechStackIds = listOf(1L, 2L, 3L, 4L)),
            )

        // required만 available: weight 50, ratio 2/4=0.5 -> rawWeightedSum=25, availableWeight=50
        // -> score = 25/50*100 = 50
        assertThat(result).isNotNull
        assertThat(result!!.score).isEqualTo(50)
    }

    @Test
    fun `필수 기술이 하나도 일치하지 않으면 해당 Factor 기여가 0이다`() {
        val result =
            calculateScore(
                memberTechStackIds = setOf(9L),
                aiSnapshot = snapshotOf(requiredTechStackIds = listOf(1L, 2L)),
            )

        assertThat(result).isNotNull
        assertThat(result!!.score).isEqualTo(0)
    }

    @Test
    fun `우대 기술 일치는 Reason과 점수에 반영된다`() {
        val result =
            calculateScore(
                memberTechStackIds = setOf(5L),
                aiSnapshot = snapshotOf(preferredTechStackIds = listOf(5L, 6L)),
            )

        assertThat(result).isNotNull
        assertThat(
            result!!.reasons,
        ).contains(RecommendationReason(PREFERRED_SKILL_MATCH, matchedCount = 1, totalCount = 2))
    }

    @Test
    fun `required와 preferred에 동시에 존재하는 기술은 preferred에서 중복 제거된다`() {
        // required=[1,2], preferred=[2,3] -> 2는 required 우선, preferred 유효 집합은 {3}뿐
        val result =
            calculateScore(
                memberTechStackIds = setOf(2L, 3L),
                aiSnapshot = snapshotOf(requiredTechStackIds = listOf(1L, 2L), preferredTechStackIds = listOf(2L, 3L)),
            )

        assertThat(result).isNotNull
        assertThat(result!!.reasons)
            .contains(RecommendationReason(PREFERRED_SKILL_MATCH, matchedCount = 1, totalCount = 1))
    }

    @Test
    fun `필수 기술이 비어 있으면 Weight를 나머지 Factor로 재정규화한다`() {
        // required 없음(unavailable) -> highSchool(15)+entry(10)+difficulty(5)=30이 available
        // highSchool SUITABLE(1.0*15=15), entry CONDITIONAL(0.5*10=5), difficulty NORMAL(0.7*5=3.5)
        // rawWeightedSum=23.5, availableWeight=30 -> score = 23.5/30*100 = 78.33... -> round 78
        val result =
            calculateScore(
                memberTechStackIds = emptySet(),
                aiSnapshot =
                    snapshotOf(
                        highSchoolGraduateFit = "SUITABLE",
                        entryLevelFit = "CONDITIONAL",
                        difficulty = "NORMAL",
                    ),
            )

        assertThat(result).isNotNull
        assertThat(result!!.score).isEqualTo(78)
    }

    @Test
    fun `우대 기술이 비어 있어도 다른 Factor로 정상 계산한다`() {
        val result =
            calculateScore(
                memberTechStackIds = setOf(1L),
                aiSnapshot = snapshotOf(requiredTechStackIds = listOf(1L), preferredTechStackIds = emptyList()),
            )

        assertThat(result).isNotNull
        assertThat(result!!.score).isEqualTo(100)
    }

    @Test
    fun `필수와 우대 기술이 모두 비어 있어도 Fit Factor만으로 계산 가능하다`() {
        val result =
            calculateScore(
                memberTechStackIds = emptySet(),
                aiSnapshot = snapshotOf(highSchoolGraduateFit = "SUITABLE"),
            )

        assertThat(result).isNotNull
        assertThat(result!!.score).isEqualTo(100)
    }

    @Test
    fun `highSchoolGraduateFit SUITABLE이면 Reason에 포함된다`() {
        val result =
            calculateScore(setOf(1L), snapshotOf(requiredTechStackIds = listOf(1L), highSchoolGraduateFit = "SUITABLE"))

        assertThat(result!!.reasons).contains(RecommendationReason(HIGH_SCHOOL_SUITABLE))
    }

    @Test
    fun `highSchoolGraduateFit CONDITIONAL은 절반 가중치로 반영되고 Reason에는 없다`() {
        val result =
            calculateScore(emptySet(), snapshotOf(highSchoolGraduateFit = "CONDITIONAL"))

        assertThat(result).isNotNull
        assertThat(result!!.score).isEqualTo(50)
        assertThat(result.reasons).isEmpty()
    }

    @Test
    fun `entryLevelFit SUITABLE이면 Reason에 포함된다`() {
        val result =
            calculateScore(setOf(1L), snapshotOf(requiredTechStackIds = listOf(1L), entryLevelFit = "SUITABLE"))

        assertThat(result!!.reasons).contains(RecommendationReason(ENTRY_LEVEL_SUITABLE))
    }

    @Test
    fun `entryLevelFit UNSUITABLE은 Hard Filter 대상이 아니라 Score만 0으로 반영된다`() {
        val result = calculateScore(emptySet(), snapshotOf(entryLevelFit = "UNSUITABLE"))

        assertThat(result).isNotNull
        assertThat(result!!.score).isEqualTo(0)
    }

    @Test
    fun `difficulty EASY는 HARD보다 높은 점수를 준다`() {
        val easy = calculateScore(emptySet(), snapshotOf(difficulty = "EASY"))!!.score
        val normal = calculateScore(emptySet(), snapshotOf(difficulty = "NORMAL"))!!.score
        val hard = calculateScore(emptySet(), snapshotOf(difficulty = "HARD"))!!.score

        assertThat(easy).isGreaterThan(normal)
        assertThat(normal).isGreaterThan(hard)
    }

    @Test
    fun `difficulty가 HARD여도 0점으로 만들지 않는다`() {
        val result = calculateScore(emptySet(), snapshotOf(difficulty = "HARD"))

        assertThat(result).isNotNull
        assertThat(result!!.score).isGreaterThan(0)
    }

    @Test
    fun `difficulty는 Reason에 포함되지 않는다`() {
        val result = calculateScore(setOf(1L), snapshotOf(requiredTechStackIds = listOf(1L), difficulty = "EASY"))

        // difficulty는 Score에는 반영되지만(위 EASY/NORMAL/HARD 비교 Test) Reason에는 절대
        // 담기지 않는다 -- required match 1건만 Reason으로 남아야 한다.
        assertThat(
            result!!.reasons,
        ).containsExactly(RecommendationReason(REQUIRED_SKILL_MATCH, matchedCount = 1, totalCount = 1))
    }

    @Test
    fun `required match 0건이면 REQUIRED_SKILL_MATCH Reason이 생기지 않는다`() {
        val result = calculateScore(setOf(9L), snapshotOf(requiredTechStackIds = listOf(1L)))

        assertThat(result!!.reasons.map { it.type }).doesNotContain(REQUIRED_SKILL_MATCH)
    }

    @Test
    fun `Score는 항상 0 이상 100 이하다`() {
        val zero = calculateScore(emptySet(), snapshotOf(requiredTechStackIds = listOf(1L, 2L)))!!.score
        val hundred =
            calculateScore(
                setOf(1L, 2L),
                snapshotOf(
                    requiredTechStackIds = listOf(1L, 2L),
                    highSchoolGraduateFit = "SUITABLE",
                    entryLevelFit = "SUITABLE",
                ),
            )!!.score

        assertThat(zero).isBetween(0, 100)
        assertThat(hundred).isBetween(0, 100)
    }

    @Test
    fun `소수점 결과는 반올림한다`() {
        // required 1/3 match -> weight 50 * (1/3) = 16.666... -> availableWeight=50
        // -> score = 16.666/50*100 = 33.33... -> round 33
        val result = calculateScore(setOf(1L), snapshotOf(requiredTechStackIds = listOf(1L, 2L, 3L)))

        assertThat(result!!.score).isEqualTo(33)
    }

    @Test
    fun `suitabilityOf는 Score 구간을 SuitabilityLevel로 변환한다`() {
        assertThat(suitabilityOf(0)).isEqualTo(SuitabilityLevel.VERY_UNSUITABLE)
        assertThat(suitabilityOf(19)).isEqualTo(SuitabilityLevel.VERY_UNSUITABLE)
        assertThat(suitabilityOf(20)).isEqualTo(SuitabilityLevel.UNSUITABLE)
        assertThat(suitabilityOf(39)).isEqualTo(SuitabilityLevel.UNSUITABLE)
        assertThat(suitabilityOf(40)).isEqualTo(SuitabilityLevel.NORMAL)
        assertThat(suitabilityOf(59)).isEqualTo(SuitabilityLevel.NORMAL)
        assertThat(suitabilityOf(60)).isEqualTo(SuitabilityLevel.RECOMMENDED)
        assertThat(suitabilityOf(79)).isEqualTo(SuitabilityLevel.RECOMMENDED)
        assertThat(suitabilityOf(80)).isEqualTo(SuitabilityLevel.HIGHLY_RECOMMENDED)
        assertThat(suitabilityOf(100)).isEqualTo(SuitabilityLevel.HIGHLY_RECOMMENDED)
    }
}
