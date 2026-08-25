package team.inreok.getiserver.domain.recommendation.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import team.inreok.getiserver.domain.ai.query.AiAnalysisSearchSnapshot
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.PostingType
import team.inreok.getiserver.domain.job.query.JobRecommendationCandidateSnapshot
import team.inreok.getiserver.domain.member.query.RecommendationMemberProfileSnapshot
import team.inreok.getiserver.domain.recommendation.entity.type.RecommendationExclusionReason.ALREADY_APPLIED
import team.inreok.getiserver.domain.recommendation.entity.type.RecommendationExclusionReason.ALREADY_BOOKMARKED
import team.inreok.getiserver.domain.recommendation.entity.type.RecommendationExclusionReason.HIGH_SCHOOL_UNSUITABLE
import team.inreok.getiserver.domain.recommendation.entity.type.RecommendationExclusionReason.JOB_NOT_PUBLISHED
import team.inreok.getiserver.domain.recommendation.entity.type.RecommendationExclusionReason.NOT_INTERESTED
import team.inreok.getiserver.domain.recommendation.entity.type.RecommendationExclusionReason.RECRUITMENT_ENDED
import team.inreok.getiserver.domain.recommendation.entity.type.RecommendationExclusionReason.SIMILAR_JOB_EXCLUDED
import team.inreok.getiserver.domain.recommendation.entity.type.RecommendationExclusionReason.TARGET_GRADE_MISMATCH
import java.time.LocalDateTime

class RecommendationCandidateFilterTest {
    private val now = LocalDateTime.of(2026, 8, 16, 12, 0, 0)

    private fun jobOf(
        status: String = "PUBLISHED",
        targetGrade: Int? = null,
        recruitmentEndedAt: LocalDateTime? = null,
    ) = JobRecommendationCandidateSnapshot(
        jobId = 1L,
        companyId = 1L,
        title = "백엔드 개발자",
        status = status,
        targetGrade = targetGrade,
        publishedAt = now.minusDays(1),
        recruitmentEndedAt = recruitmentEndedAt,
        postingType = PostingType.GENERAL,
        applicationMethod = ApplicationMethod.INTERNAL,
        viewCount = 0,
    )

    private fun memberOf(
        grade: Int? = 3,
        techStackIds: Set<Long> = emptySet(),
    ) = RecommendationMemberProfileSnapshot(
        memberId = 100L,
        status = "ACTIVE",
        grade = grade,
        techStackIds = techStackIds,
    )

    private fun aiSnapshotOf(highSchoolGraduateFit: String? = "SUITABLE") =
        AiAnalysisSearchSnapshot(
            requiredTechStackIds = emptyList(),
            preferredTechStackIds = emptyList(),
            highSchoolGraduateFit = highSchoolGraduateFit,
            entryLevelFit = "SUITABLE",
            difficulty = "NORMAL",
        )

    @Test
    fun `PUBLISHED가 아니면 JOB_NOT_PUBLISHED로 제외한다`() {
        val reason =
            computeExclusionReason(
                job = jobOf(status = "CLOSED"),
                member = memberOf(),
                excludedJobIds = emptySet(),
                bookmarkedJobIds = emptySet(),
                aiSnapshot = aiSnapshotOf(),
                hasActiveApplication = false,
                isSimilarToExcludedJob = false,
                now = now,
            )

        assertThat(reason).isEqualTo(JOB_NOT_PUBLISHED)
    }

    @Test
    fun `recruitmentEndedAt이 지났으면 RECRUITMENT_ENDED로 제외한다`() {
        val reason =
            computeExclusionReason(
                job = jobOf(recruitmentEndedAt = now.minusHours(1)),
                member = memberOf(),
                excludedJobIds = emptySet(),
                bookmarkedJobIds = emptySet(),
                aiSnapshot = aiSnapshotOf(),
                hasActiveApplication = false,
                isSimilarToExcludedJob = false,
                now = now,
            )

        assertThat(reason).isEqualTo(RECRUITMENT_ENDED)
    }

    @Test
    fun `recruitmentEndedAt이 아직 지나지 않았으면 통과한다`() {
        val reason =
            computeExclusionReason(
                job = jobOf(recruitmentEndedAt = now.plusHours(1)),
                member = memberOf(),
                excludedJobIds = emptySet(),
                bookmarkedJobIds = emptySet(),
                aiSnapshot = aiSnapshotOf(),
                hasActiveApplication = false,
                isSimilarToExcludedJob = false,
                now = now,
            )

        assertThat(reason).isNull()
    }

    @Test
    fun `대상 학년이 다르면 TARGET_GRADE_MISMATCH로 제외한다`() {
        val reason =
            computeExclusionReason(
                job = jobOf(targetGrade = 2),
                member = memberOf(grade = 3),
                excludedJobIds = emptySet(),
                bookmarkedJobIds = emptySet(),
                aiSnapshot = aiSnapshotOf(),
                hasActiveApplication = false,
                isSimilarToExcludedJob = false,
                now = now,
            )

        assertThat(reason).isEqualTo(TARGET_GRADE_MISMATCH)
    }

    @Test
    fun `targetGrade가 null이면 학년 불일치로 제외하지 않는다`() {
        val reason =
            computeExclusionReason(
                job = jobOf(targetGrade = null),
                member = memberOf(grade = 3),
                excludedJobIds = emptySet(),
                bookmarkedJobIds = emptySet(),
                aiSnapshot = aiSnapshotOf(),
                hasActiveApplication = false,
                isSimilarToExcludedJob = false,
                now = now,
            )

        assertThat(reason).isNull()
    }

    @Test
    fun `member grade가 null이면 학년 불일치로 제외하지 않는다`() {
        val reason =
            computeExclusionReason(
                job = jobOf(targetGrade = 3),
                member = memberOf(grade = null),
                excludedJobIds = emptySet(),
                bookmarkedJobIds = emptySet(),
                aiSnapshot = aiSnapshotOf(),
                hasActiveApplication = false,
                isSimilarToExcludedJob = false,
                now = now,
            )

        assertThat(reason).isNull()
    }

    @Test
    fun `이미 활성 지원서가 있으면 ALREADY_APPLIED로 제외한다`() {
        val reason =
            computeExclusionReason(
                job = jobOf(),
                member = memberOf(),
                excludedJobIds = emptySet(),
                bookmarkedJobIds = emptySet(),
                aiSnapshot = aiSnapshotOf(),
                hasActiveApplication = true,
                isSimilarToExcludedJob = false,
                now = now,
            )

        assertThat(reason).isEqualTo(ALREADY_APPLIED)
    }

    @Test
    fun `이미 지원한 공고가 관심 없음-북마크보다 우선한다`() {
        val reason =
            computeExclusionReason(
                job = jobOf(),
                member = memberOf(),
                excludedJobIds = setOf(1L),
                bookmarkedJobIds = setOf(1L),
                aiSnapshot = aiSnapshotOf(),
                hasActiveApplication = true,
                isSimilarToExcludedJob = false,
                now = now,
            )

        assertThat(reason).isEqualTo(ALREADY_APPLIED)
    }

    @Test
    fun `관심 없음 처리한 공고는 NOT_INTERESTED로 제외한다`() {
        val reason =
            computeExclusionReason(
                job = jobOf(),
                member = memberOf(),
                excludedJobIds = setOf(1L),
                bookmarkedJobIds = emptySet(),
                aiSnapshot = aiSnapshotOf(),
                hasActiveApplication = false,
                isSimilarToExcludedJob = false,
                now = now,
            )

        assertThat(reason).isEqualTo(NOT_INTERESTED)
    }

    @Test
    fun `이미 북마크한 공고는 ALREADY_BOOKMARKED로 제외한다`() {
        val reason =
            computeExclusionReason(
                job = jobOf(),
                member = memberOf(),
                excludedJobIds = emptySet(),
                bookmarkedJobIds = setOf(1L),
                aiSnapshot = aiSnapshotOf(),
                hasActiveApplication = false,
                isSimilarToExcludedJob = false,
                now = now,
            )

        assertThat(reason).isEqualTo(ALREADY_BOOKMARKED)
    }

    @Test
    fun `관심 없음이 북마크보다 우선한다(둘 다 해당해도 NOT_INTERESTED)`() {
        val reason =
            computeExclusionReason(
                job = jobOf(),
                member = memberOf(),
                excludedJobIds = setOf(1L),
                bookmarkedJobIds = setOf(1L),
                aiSnapshot = aiSnapshotOf(),
                hasActiveApplication = false,
                isSimilarToExcludedJob = false,
                now = now,
            )

        assertThat(reason).isEqualTo(NOT_INTERESTED)
    }

    @Test
    fun `SIMILAR_JOBS로 유사 판정된 공고는 SIMILAR_JOB_EXCLUDED로 제외한다`() {
        val reason =
            computeExclusionReason(
                job = jobOf(),
                member = memberOf(),
                excludedJobIds = emptySet(),
                bookmarkedJobIds = emptySet(),
                aiSnapshot = aiSnapshotOf(),
                hasActiveApplication = false,
                isSimilarToExcludedJob = true,
                now = now,
            )

        assertThat(reason).isEqualTo(SIMILAR_JOB_EXCLUDED)
    }

    @Test
    fun `관심 없음(자기 자신)이 유사 판정보다 우선한다`() {
        val reason =
            computeExclusionReason(
                job = jobOf(),
                member = memberOf(),
                excludedJobIds = setOf(1L),
                bookmarkedJobIds = emptySet(),
                aiSnapshot = aiSnapshotOf(),
                hasActiveApplication = false,
                isSimilarToExcludedJob = true,
                now = now,
            )

        assertThat(reason).isEqualTo(NOT_INTERESTED)
    }

    @Test
    fun `유사 판정이 북마크보다 우선한다`() {
        val reason =
            computeExclusionReason(
                job = jobOf(),
                member = memberOf(),
                excludedJobIds = emptySet(),
                bookmarkedJobIds = setOf(1L),
                aiSnapshot = aiSnapshotOf(),
                hasActiveApplication = false,
                isSimilarToExcludedJob = true,
                now = now,
            )

        assertThat(reason).isEqualTo(SIMILAR_JOB_EXCLUDED)
    }

    @Test
    fun `고졸 부적합 공고는 HIGH_SCHOOL_UNSUITABLE로 제외한다`() {
        val reason =
            computeExclusionReason(
                job = jobOf(),
                member = memberOf(),
                excludedJobIds = emptySet(),
                bookmarkedJobIds = emptySet(),
                aiSnapshot = aiSnapshotOf(highSchoolGraduateFit = "UNSUITABLE"),
                hasActiveApplication = false,
                isSimilarToExcludedJob = false,
                now = now,
            )

        assertThat(reason).isEqualTo(HIGH_SCHOOL_UNSUITABLE)
    }

    @Test
    fun `AI 분석이 없어도(null) Hard Filter는 통과할 수 있다`() {
        val reason =
            computeExclusionReason(
                job = jobOf(),
                member = memberOf(),
                excludedJobIds = emptySet(),
                bookmarkedJobIds = emptySet(),
                aiSnapshot = null,
                hasActiveApplication = false,
                isSimilarToExcludedJob = false,
                now = now,
            )

        assertThat(reason).isNull()
    }

    @Test
    fun `정상 PUBLISHED 후보는 모든 조건을 통과한다`() {
        val reason =
            computeExclusionReason(
                job = jobOf(targetGrade = 3, recruitmentEndedAt = now.plusDays(10)),
                member = memberOf(grade = 3),
                excludedJobIds = emptySet(),
                bookmarkedJobIds = emptySet(),
                aiSnapshot = aiSnapshotOf(),
                hasActiveApplication = false,
                isSimilarToExcludedJob = false,
                now = now,
            )

        assertThat(reason).isNull()
    }
}
