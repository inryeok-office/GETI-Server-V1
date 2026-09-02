package team.inreok.getiserver.domain.recommendation.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import team.inreok.getiserver.domain.ai.query.AiAnalysisSearchQueryPort
import team.inreok.getiserver.domain.company.query.CompanyQuery
import team.inreok.getiserver.domain.job.access.JobAiAnalysisAccessor
import team.inreok.getiserver.domain.job.access.JobAiSkillAccessView
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.PostingType
import team.inreok.getiserver.domain.job.query.JobRecommendationCandidateQueryPort
import team.inreok.getiserver.domain.job.query.JobRecommendationCandidateSnapshot
import team.inreok.getiserver.domain.member.query.MemberApplicantSnapshotQueryPort
import team.inreok.getiserver.domain.recommendation.entity.Recommendation
import team.inreok.getiserver.domain.recommendation.entity.RecommendationPreference
import team.inreok.getiserver.domain.recommendation.entity.type.SuitabilityLevel
import team.inreok.getiserver.domain.recommendation.repository.JobBookmarkCount
import team.inreok.getiserver.domain.recommendation.repository.MemberJobPreferenceRepository
import team.inreok.getiserver.domain.recommendation.repository.RecommendationGenerationStateRepository
import team.inreok.getiserver.domain.recommendation.repository.RecommendationPreferenceRepository
import team.inreok.getiserver.domain.recommendation.repository.RecommendationRepository
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecommendationServiceImplJobSummaryTest {
    @Mock
    private lateinit var recommendationRepository: RecommendationRepository

    @Mock
    private lateinit var recommendationPreferenceRepository: RecommendationPreferenceRepository

    @Mock
    private lateinit var recommendationGenerationStateRepository: RecommendationGenerationStateRepository

    @Mock
    private lateinit var memberJobPreferenceRepository: MemberJobPreferenceRepository

    @Mock
    private lateinit var jobRecommendationCandidateQueryPort: JobRecommendationCandidateQueryPort

    @Mock
    private lateinit var companyQuery: CompanyQuery

    @Mock
    private lateinit var memberApplicantSnapshotQueryPort: MemberApplicantSnapshotQueryPort

    @Mock
    private lateinit var aiAnalysisSearchQueryPort: AiAnalysisSearchQueryPort

    @Mock
    private lateinit var jobAiAnalysisAccessor: JobAiAnalysisAccessor

    private val service by lazy {
        RecommendationServiceImpl(
            recommendationRepository,
            recommendationPreferenceRepository,
            recommendationGenerationStateRepository,
            memberJobPreferenceRepository,
            jobRecommendationCandidateQueryPort,
            companyQuery,
            memberApplicantSnapshotQueryPort,
            JsonMapper(),
            "0 0 3 * * *",
            0.6,
            aiAnalysisSearchQueryPort,
            jobAiAnalysisAccessor,
        )
    }

    private val today = LocalDate.now()
    private val firstPage = PageRequest.of(0, 20)

    private fun jobOf(
        location: String? = null,
        employmentType: String? = null,
    ) = JobRecommendationCandidateSnapshot(
        jobId = 1L,
        companyId = 100L,
        title = "백엔드 개발 인턴",
        status = "PUBLISHED",
        targetGrade = 3,
        publishedAt = LocalDateTime.of(2026, 8, 1, 0, 0),
        recruitmentEndedAt = LocalDateTime.of(2026, 12, 31, 0, 0),
        postingType = PostingType.GENERAL,
        applicationMethod = ApplicationMethod.INTERNAL,
        viewCount = 10,
        location = location,
        employmentType = employmentType,
    )

    private fun recommendationOf() =
        Recommendation(
            memberId = 1L,
            jobId = 1L,
            recommendationDate = today,
            score = BigDecimal(82),
            suitability = SuitabilityLevel.RECOMMENDED,
            rank = 1,
            algorithmVersion = 1,
        ).apply {
            id = 1L
            createdAt = LocalDateTime.of(2026, 8, 17, 6, 0)
        }

    private fun bookmarkCountOf(count: Long) =
        object : JobBookmarkCount {
            override val jobId: Long = 1L
            override val bookmarkCount: Long = count
        }

    private fun givenReadyRecommendation(job: JobRecommendationCandidateSnapshot) {
        given(recommendationPreferenceRepository.findByMemberId(1L))
            .willReturn(RecommendationPreference(memberId = 1L, enabled = true))
        given(recommendationRepository.findAllByMemberIdAndRecommendationDate(1L, today, null, firstPage))
            .willReturn(PageImpl(listOf(recommendationOf()), firstPage, 1))
        given(recommendationRepository.findMaxCreatedAtByMemberIdAndRecommendationDate(1L, today))
            .willReturn(LocalDateTime.of(2026, 8, 17, 6, 0))
        given(jobRecommendationCandidateQueryPort.findAllByIds(setOf(1L))).willReturn(mapOf(1L to job))
        given(companyQuery.findActiveSummaries(setOf(100L), 1L)).willReturn(emptyMap())
    }

    @Test
    fun `추천 목록은 Job Snapshot의 근무지역과 고용형태를 전달하고 기존 요약 필드도 유지한다`() {
        givenReadyRecommendation(jobOf(location = "서울특별시 중구", employmentType = "인턴"))
        given(jobAiAnalysisAccessor.findMatchedTechStacks(setOf(1L)))
            .willReturn(mapOf(1L to listOf(JobAiSkillAccessView(techStackId = 3L, name = "Kotlin"))))
        given(memberJobPreferenceRepository.countBookmarksByJobIds(setOf(1L)))
            .willReturn(listOf(bookmarkCountOf(9L)))

        val result = service.getMyRecommendations(1L, null, firstPage)
        val job = result.content.single().job

        assertThat(job.location).isEqualTo("서울특별시 중구")
        assertThat(job.employmentType).isEqualTo("인턴")
        assertThat(job.techStacks).containsExactly(JobAiSkillAccessView(techStackId = 3L, name = "Kotlin"))
        assertThat(job.bookmarkCount).isEqualTo(9L)
        assertThat(result.totalElements).isEqualTo(1)
        verify(jobRecommendationCandidateQueryPort, times(1)).findAllByIds(setOf(1L))
    }

    @Test
    fun `추천 목록의 근무지역과 고용형태가 없으면 null을 유지한다`() {
        givenReadyRecommendation(jobOf())

        val job =
            service
                .getMyRecommendations(1L, null, firstPage)
                .content
                .single()
                .job

        assertThat(job.location).isNull()
        assertThat(job.employmentType).isNull()
    }
}
