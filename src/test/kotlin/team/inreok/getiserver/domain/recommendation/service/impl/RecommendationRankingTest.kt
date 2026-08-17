package team.inreok.getiserver.domain.recommendation.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.PostingType
import team.inreok.getiserver.domain.job.query.JobRecommendationCandidateSnapshot
import java.time.LocalDateTime

class RecommendationRankingTest {
    private fun candidateOf(
        jobId: Long,
        score: Int,
        publishedAt: LocalDateTime? = LocalDateTime.of(2026, 8, 1, 0, 0),
    ) = ScoredRecommendationCandidate(
        job =
            JobRecommendationCandidateSnapshot(
                jobId = jobId,
                companyId = 1L,
                title = "Job $jobId",
                status = "PUBLISHED",
                targetGrade = null,
                publishedAt = publishedAt,
                recruitmentEndedAt = null,
                postingType = PostingType.GENERAL,
                applicationMethod = ApplicationMethod.INTERNAL,
                viewCount = 0,
            ),
        result = RecommendationScoreResult(score = score, reasons = emptyList()),
    )

    @Test
    fun `score 내림차순으로 정렬한다`() {
        val ranked =
            rank(
                listOf(candidateOf(1L, score = 50), candidateOf(2L, score = 90), candidateOf(3L, score = 70)),
                limit = 10,
            )

        assertThat(ranked.map { it.jobId }).containsExactly(2L, 3L, 1L)
    }

    @Test
    fun `score가 같으면 publishedAt 내림차순이다`() {
        val older = LocalDateTime.of(2026, 8, 1, 0, 0)
        val newer = LocalDateTime.of(2026, 8, 10, 0, 0)

        val ranked =
            rank(
                listOf(
                    candidateOf(1L, score = 80, publishedAt = older),
                    candidateOf(2L, score = 80, publishedAt = newer),
                ),
                limit = 10,
            )

        assertThat(ranked.map { it.jobId }).containsExactly(2L, 1L)
    }

    @Test
    fun `score와 publishedAt이 모두 같으면 jobId 내림차순이다`() {
        val ranked =
            rank(
                listOf(candidateOf(5L, score = 80), candidateOf(9L, score = 80), candidateOf(3L, score = 80)),
                limit = 10,
            )

        assertThat(ranked.map { it.jobId }).containsExactly(9L, 5L, 3L)
    }

    @Test
    fun `rank는 1부터 순서대로 매겨진다`() {
        val ranked = rank(listOf(candidateOf(1L, 90), candidateOf(2L, 80), candidateOf(3L, 70)), limit = 10)

        assertThat(ranked.map { it.rank }).containsExactly(1, 2, 3)
    }

    @Test
    fun `limit을 초과하는 후보는 잘라낸다`() {
        val ranked =
            rank(
                listOf(candidateOf(1L, 90), candidateOf(2L, 80), candidateOf(3L, 70)),
                limit = 2,
            )

        assertThat(ranked).hasSize(2)
        assertThat(ranked.map { it.jobId }).containsExactly(1L, 2L)
    }

    @Test
    fun `같은 입력을 반복 계산해도 항상 같은 순서다`() {
        val candidates = listOf(candidateOf(1L, 80), candidateOf(2L, 80), candidateOf(3L, 90))

        val first = rank(candidates, limit = 10).map { it.jobId }
        val second = rank(candidates, limit = 10).map { it.jobId }

        assertThat(first).isEqualTo(second)
    }

    @Test
    fun `publishedAt이 null인 후보는 가장 오래된 것으로 취급한다`() {
        val ranked =
            rank(
                listOf(
                    candidateOf(1L, score = 80, publishedAt = null),
                    candidateOf(2L, score = 80, publishedAt = LocalDateTime.of(2026, 1, 1, 0, 0)),
                ),
                limit = 10,
            )

        assertThat(ranked.map { it.jobId }).containsExactly(2L, 1L)
    }
}
