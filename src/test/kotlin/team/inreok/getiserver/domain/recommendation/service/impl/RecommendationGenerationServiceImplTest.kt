package team.inreok.getiserver.domain.recommendation.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyCollection
import org.mockito.ArgumentMatchers.anyList
import org.mockito.BDDMockito.given
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.ai.query.AiAnalysisSearchQueryPort
import team.inreok.getiserver.domain.ai.query.AiAnalysisSearchSnapshot
import team.inreok.getiserver.domain.job.query.JobRecommendationCandidateQueryPort
import team.inreok.getiserver.domain.job.query.JobRecommendationCandidateSnapshot
import team.inreok.getiserver.domain.member.query.RecommendationMemberProfileQueryPort
import team.inreok.getiserver.domain.member.query.RecommendationMemberProfileSnapshot
import team.inreok.getiserver.domain.recommendation.entity.Recommendation
import team.inreok.getiserver.domain.recommendation.repository.MemberJobPreferenceRepository
import team.inreok.getiserver.domain.recommendation.repository.RecommendationRepository
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class RecommendationGenerationServiceImplTest {
    @Mock
    private lateinit var memberProfileQueryPort: RecommendationMemberProfileQueryPort

    @Mock
    private lateinit var jobCandidateQueryPort: JobRecommendationCandidateQueryPort

    @Mock
    private lateinit var aiAnalysisSearchQueryPort: AiAnalysisSearchQueryPort

    @Mock
    private lateinit var memberJobPreferenceRepository: MemberJobPreferenceRepository

    @Mock
    private lateinit var recommendationRepository: RecommendationRepository

    @Captor
    private lateinit var savedCaptor: ArgumentCaptor<List<Recommendation>>

    private val objectMapper = ObjectMapper()

    private val service by lazy {
        RecommendationGenerationServiceImpl(
            memberProfileQueryPort,
            jobCandidateQueryPort,
            aiAnalysisSearchQueryPort,
            memberJobPreferenceRepository,
            recommendationRepository,
            objectMapper,
        )
    }

    private fun memberOf(techStackIds: Set<Long> = setOf(1L)) =
        RecommendationMemberProfileSnapshot(memberId = 1L, status = "ACTIVE", grade = 3, techStackIds = techStackIds)

    private fun jobOf(id: Long) =
        JobRecommendationCandidateSnapshot(
            jobId = id,
            companyId = 100L,
            title = "Job $id",
            status = "PUBLISHED",
            targetGrade = 3,
            publishedAt = LocalDateTime.of(2026, 8, 1, 0, 0),
            recruitmentEndedAt = LocalDateTime.of(2026, 12, 31, 0, 0),
        )

    private fun aiSnapshotOf(requiredTechStackIds: List<Long> = listOf(1L)) =
        AiAnalysisSearchSnapshot(
            requiredTechStackIds = requiredTechStackIds,
            preferredTechStackIds = emptyList(),
            highSchoolGraduateFit = "SUITABLE",
            entryLevelFit = "SUITABLE",
            difficulty = "NORMAL",
        )

    @Test
    fun `Member Profile을 찾을 수 없으면 아무것도 하지 않고 빈 목록을 반환한다`() {
        given(memberProfileQueryPort.findById(1L)).willReturn(null)

        val result = service.generateForMember(1L, limit = 10)

        assertThat(result).isEmpty()
        verifyNoInteractions(jobCandidateQueryPort, aiAnalysisSearchQueryPort, recommendationRepository)
    }

    @Test
    fun `Candidate가 100개여도 AI Query는 한 번만 호출한다(Batch, N+1 방지)`() {
        val jobs = (1..100L).map { jobOf(it) }
        given(memberProfileQueryPort.findById(1L)).willReturn(memberOf())
        given(jobCandidateQueryPort.findAllPublished()).willReturn(jobs)
        given(memberJobPreferenceRepository.findExcludedJobIdsByMemberId(1L)).willReturn(emptyList())
        given(aiAnalysisSearchQueryPort.findCompletedByJobIds(jobs.map { it.jobId })).willReturn(
            jobs.associate { it.jobId to aiSnapshotOf() },
        )

        service.generateForMember(1L, limit = 10)

        verify(aiAnalysisSearchQueryPort, times(1)).findCompletedByJobIds(anyCollection())
    }

    @Test
    fun `Hard Filter와 Score 계산을 통과한 후보만 저장하고 기존 오늘자 결과를 교체한다`() {
        val jobs = listOf(jobOf(1L), jobOf(2L))
        given(memberProfileQueryPort.findById(1L)).willReturn(memberOf())
        given(jobCandidateQueryPort.findAllPublished()).willReturn(jobs)
        given(memberJobPreferenceRepository.findExcludedJobIdsByMemberId(1L)).willReturn(emptyList())
        given(aiAnalysisSearchQueryPort.findCompletedByJobIds(jobs.map { it.jobId })).willReturn(
            mapOf(1L to aiSnapshotOf(), 2L to aiSnapshotOf()),
        )

        val result = service.generateForMember(1L, limit = 10)

        assertThat(result).hasSize(2)
        verify(recommendationRepository).deleteAllByMemberIdAndRecommendationDate(1L, LocalDate.now())
        verify(recommendationRepository).saveAll(savedCaptor.capture())
        val saved = savedCaptor.value
        assertThat(saved).hasSize(2)
        assertThat(saved.map { it.algorithmVersion }).containsOnly(RECOMMENDATION_ALGORITHM_VERSION)
        assertThat(saved.map { it.rank }).containsExactlyInAnyOrder(1, 2)
    }

    @Test
    fun `관심 없음 처리한 공고는 제외되고 남은 후보가 없으면 삭제만 하고 저장은 하지 않는다`() {
        val jobs = listOf(jobOf(1L))
        given(memberProfileQueryPort.findById(1L)).willReturn(memberOf())
        given(jobCandidateQueryPort.findAllPublished()).willReturn(jobs)
        given(memberJobPreferenceRepository.findExcludedJobIdsByMemberId(1L)).willReturn(listOf(1L))
        given(aiAnalysisSearchQueryPort.findCompletedByJobIds(jobs.map { it.jobId })).willReturn(
            mapOf(1L to aiSnapshotOf()),
        )

        val result = service.generateForMember(1L, limit = 10)

        assertThat(result).isEmpty()
        verify(recommendationRepository).deleteAllByMemberIdAndRecommendationDate(1L, LocalDate.now())
        verify(recommendationRepository, never()).saveAll(anyList())
    }
}
