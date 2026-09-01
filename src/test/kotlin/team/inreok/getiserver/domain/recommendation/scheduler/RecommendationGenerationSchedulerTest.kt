package team.inreok.getiserver.domain.recommendation.scheduler

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.member.query.RecommendationAudienceQueryPort
import team.inreok.getiserver.domain.recommendation.repository.RecommendationPreferenceRepository
import team.inreok.getiserver.domain.recommendation.service.RecommendationGenerationRunner

@ExtendWith(MockitoExtension::class)
class RecommendationGenerationSchedulerTest {
    @Mock
    private lateinit var recommendationPreferenceRepository: RecommendationPreferenceRepository

    @Mock
    private lateinit var recommendationAudienceQueryPort: RecommendationAudienceQueryPort

    @Mock
    private lateinit var recommendationGenerationRunner: RecommendationGenerationRunner

    private val scheduler by lazy {
        RecommendationGenerationScheduler(
            recommendationPreferenceRepository,
            recommendationAudienceQueryPort,
            recommendationGenerationRunner,
        )
    }

    @Test
    fun `추천 활성화된 재학생만 대상으로 Runner를 호출한다`() {
        given(recommendationPreferenceRepository.findMemberIdsByEnabledTrue()).willReturn(listOf(1L, 2L, 3L))
        given(recommendationAudienceQueryPort.filterEligibleStudentIds(listOf(1L, 2L, 3L))).willReturn(listOf(1L, 3L))

        scheduler.generateDailyRecommendations()

        verify(recommendationGenerationRunner).generateForMember(1L)
        verify(recommendationGenerationRunner).generateForMember(3L)
        verify(recommendationGenerationRunner, never()).generateForMember(2L)
    }

    @Test
    fun `한 회원 처리 실패가 나머지 회원 처리를 막지 않는다`() {
        given(recommendationPreferenceRepository.findMemberIdsByEnabledTrue()).willReturn(listOf(1L, 2L))
        given(recommendationAudienceQueryPort.filterEligibleStudentIds(listOf(1L, 2L))).willReturn(listOf(1L, 2L))
        given(recommendationGenerationRunner.generateForMember(1L))
            .willThrow(IllegalStateException("회원 1L 처리 실패"))

        scheduler.generateDailyRecommendations()

        verify(recommendationGenerationRunner).generateForMember(1L)
        verify(recommendationGenerationRunner).generateForMember(2L)
    }

    @Test
    fun `대상 회원이 없으면 Runner를 호출하지 않는다`() {
        given(recommendationPreferenceRepository.findMemberIdsByEnabledTrue()).willReturn(emptyList())
        given(recommendationAudienceQueryPort.filterEligibleStudentIds(emptyList())).willReturn(emptyList())

        scheduler.generateDailyRecommendations()

        verify(recommendationGenerationRunner, never()).generateForMember(anyLong())
    }
}
