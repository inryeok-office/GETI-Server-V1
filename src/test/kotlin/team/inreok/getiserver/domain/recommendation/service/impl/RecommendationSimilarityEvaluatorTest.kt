package team.inreok.getiserver.domain.recommendation.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import team.inreok.getiserver.domain.ai.query.AiAnalysisSearchSnapshot

class RecommendationSimilarityEvaluatorTest {
    private fun aiSnapshotOf(
        requiredTechStackIds: List<Long> = emptyList(),
        preferredTechStackIds: List<Long> = emptyList(),
    ) = AiAnalysisSearchSnapshot(
        requiredTechStackIds = requiredTechStackIds,
        preferredTechStackIds = preferredTechStackIds,
        highSchoolGraduateFit = "SUITABLE",
        entryLevelFit = "SUITABLE",
        difficulty = "NORMAL",
    )

    // ---------- techStackSetOf ----------

    @Test
    fun `required와 preferred를 합쳐 하나의 Set을 만든다`() {
        val snapshot = aiSnapshotOf(requiredTechStackIds = listOf(1L, 2L), preferredTechStackIds = listOf(2L, 3L))

        val techStackIds = techStackSetOf(snapshot)

        assertThat(techStackIds).containsExactlyInAnyOrder(1L, 2L, 3L)
    }

    @Test
    fun `required와 preferred가 모두 비어 있으면 빈 Set이다`() {
        val techStackIds = techStackSetOf(aiSnapshotOf())

        assertThat(techStackIds).isEmpty()
    }

    // ---------- computeJaccardSimilarity ----------

    @Test
    fun `기술 스택이 완전히 같으면 유사도는 1_0이다`() {
        val similarity = computeJaccardSimilarity(setOf(1L, 2L, 3L), setOf(1L, 2L, 3L))

        assertThat(similarity).isEqualTo(1.0)
    }

    @Test
    fun `기술 스택이 전혀 겹치지 않으면 유사도는 0_0이다`() {
        val similarity = computeJaccardSimilarity(setOf(1L, 2L), setOf(3L, 4L))

        assertThat(similarity).isEqualTo(0.0)
    }

    @Test
    fun `일부만 겹치면 교집합-합집합 비율이다`() {
        // 교집합 {2,3} = 2개, 합집합 {1,2,3,4} = 4개 -> 0.5
        val similarity = computeJaccardSimilarity(setOf(1L, 2L, 3L), setOf(2L, 3L, 4L))

        assertThat(similarity).isCloseTo(0.5, within(0.0001))
    }

    @Test
    fun `Source가 비어 있으면 null이다(0-0을 1_0으로 처리하지 않는다)`() {
        val similarity = computeJaccardSimilarity(emptySet(), setOf(1L))

        assertThat(similarity).isNull()
    }

    @Test
    fun `Candidate가 비어 있으면 null이다`() {
        val similarity = computeJaccardSimilarity(setOf(1L), emptySet())

        assertThat(similarity).isNull()
    }

    @Test
    fun `둘 다 비어 있어도 null이다`() {
        val similarity = computeJaccardSimilarity(emptySet(), emptySet())

        assertThat(similarity).isNull()
    }

    // ---------- isSimilarJob (Threshold 0.6 경계) ----------

    @Test
    fun `유사도가 Threshold보다 크면 SIMILAR다`() {
        // 교집합 4, 합집합 5 -> 0.8
        val isSimilar =
            isSimilarJob(
                sourceTechStackIds = setOf(1L, 2L, 3L, 4L),
                candidateTechStackIds = setOf(1L, 2L, 3L, 4L, 5L),
                threshold = 0.6,
            )

        assertThat(isSimilar).isTrue()
    }

    @Test
    fun `유사도가 Threshold와 정확히 같으면 SIMILAR다(경계값 포함)`() {
        // 교집합 3, 합집합 5 -> 0.6
        val isSimilar =
            isSimilarJob(
                sourceTechStackIds = setOf(1L, 2L, 3L),
                candidateTechStackIds = setOf(1L, 2L, 3L, 4L, 5L),
                threshold = 0.6,
            )

        assertThat(isSimilar).isTrue()
    }

    @Test
    fun `유사도가 Threshold보다 작으면 NOT_SIMILAR다`() {
        // 교집합 1, 합집합 4 -> 0.25
        val isSimilar =
            isSimilarJob(
                sourceTechStackIds = setOf(1L, 2L),
                candidateTechStackIds = setOf(1L, 3L, 4L),
                threshold = 0.6,
            )

        assertThat(isSimilar).isFalse()
    }

    @Test
    fun `비교 불가(Set이 비어 있음)면 NOT_SIMILAR다`() {
        val isSimilar = isSimilarJob(emptySet(), setOf(1L), threshold = 0.6)

        assertThat(isSimilar).isFalse()
    }

    @Test
    fun `같은 입력이면 항상 같은 결과다(deterministic)`() {
        val source = setOf(1L, 2L, 3L)
        val candidate = setOf(2L, 3L, 4L)

        val first = isSimilarJob(source, candidate, threshold = 0.6)
        val second = isSimilarJob(source, candidate, threshold = 0.6)

        assertThat(first).isEqualTo(second)
    }

    // ---------- isSimilarToAnySource ----------

    @Test
    fun `여러 기준 Job 중 하나라도 유사하면 true다`() {
        val candidateSnapshot = aiSnapshotOf(requiredTechStackIds = listOf(1L, 2L, 3L))
        val sourceTechStackSets = listOf(setOf(9L, 10L), setOf(1L, 2L, 3L))

        val isSimilar = isSimilarToAnySource(candidateSnapshot, sourceTechStackSets, threshold = 0.6)

        assertThat(isSimilar).isTrue()
    }

    @Test
    fun `어떤 기준 Job과도 유사하지 않으면 false다`() {
        val candidateSnapshot = aiSnapshotOf(requiredTechStackIds = listOf(1L, 2L, 3L))
        val sourceTechStackSets = listOf(setOf(9L, 10L), setOf(11L, 12L))

        val isSimilar = isSimilarToAnySource(candidateSnapshot, sourceTechStackSets, threshold = 0.6)

        assertThat(isSimilar).isFalse()
    }

    @Test
    fun `Candidate AI 분석이 없으면 비교하지 않고 false다`() {
        val isSimilar = isSimilarToAnySource(null, listOf(setOf(1L, 2L, 3L)), threshold = 0.6)

        assertThat(isSimilar).isFalse()
    }

    @Test
    fun `기준 Job이 하나도 없으면 false다`() {
        val candidateSnapshot = aiSnapshotOf(requiredTechStackIds = listOf(1L, 2L, 3L))

        val isSimilar = isSimilarToAnySource(candidateSnapshot, emptyList(), threshold = 0.6)

        assertThat(isSimilar).isFalse()
    }

    @Test
    fun `required-preferred 위치가 서로 달라도 합집합 기준으로 같은 기술이면 매칭된다`() {
        // Source는 1L을 required로, Candidate는 1L을 preferred로 갖고 있어도 techStackSetOf는
        // 둘을 합쳐 비교하므로 위치와 무관하게 매칭된다.
        val sourceSnapshot = aiSnapshotOf(requiredTechStackIds = listOf(1L, 2L, 3L))
        val candidateSnapshot = aiSnapshotOf(preferredTechStackIds = listOf(1L, 2L, 3L))

        val isSimilar =
            isSimilarToAnySource(
                candidateSnapshot,
                listOf(techStackSetOf(sourceSnapshot)),
                threshold = 0.6,
            )

        assertThat(isSimilar).isTrue()
    }
}
