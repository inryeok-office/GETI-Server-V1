package team.inreok.getiserver.domain.recommendation.service.impl

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.eq
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.recommendation.service.RankedRecommendation
import team.inreok.getiserver.domain.recommendation.service.RecommendationGenerationService
import team.inreok.getiserver.domain.recommendation.service.RecommendationGenerationStateService

@ExtendWith(MockitoExtension::class)
class RecommendationGenerationRunnerImplTest {
    @Mock
    private lateinit var generationStateService: RecommendationGenerationStateService

    @Mock
    private lateinit var recommendationGenerationService: RecommendationGenerationService

    private val runner by lazy {
        RecommendationGenerationRunnerImpl(generationStateService, recommendationGenerationService, TOP_N)
    }

    @Test
    fun `계산 결과가 있으면 GENERATING을 기록한 뒤 READY로 완료 처리한다`() {
        val ranked = listOf(RankedRecommendation(jobId = 1L, score = 90, rank = 1, reasons = emptyList()))
        given(recommendationGenerationService.generateForMember(1L, TOP_N)).willReturn(ranked)

        runner.generateForMember(1L)

        val order = inOrder(generationStateService, recommendationGenerationService)
        order.verify(generationStateService).markGenerating(1L)
        order.verify(recommendationGenerationService).generateForMember(1L, TOP_N)
        order.verify(generationStateService).markCompleted(1L, hasResults = true)
        verify(generationStateService, never()).markFailed(1L)
    }

    @Test
    fun `계산 결과가 비어 있으면 EMPTY로 완료 처리한다`() {
        given(recommendationGenerationService.generateForMember(1L, TOP_N)).willReturn(emptyList())

        runner.generateForMember(1L)

        verify(generationStateService).markCompleted(1L, hasResults = false)
    }

    @Test
    fun `계산이 예외를 던지면 FAILED로 기록하고 예외를 그대로 다시 던진다`() {
        val failure = IllegalStateException("candidate 조회 실패")
        given(recommendationGenerationService.generateForMember(1L, TOP_N)).willThrow(failure)

        assertThatThrownBy { runner.generateForMember(1L) }.isSameAs(failure)

        verify(generationStateService).markGenerating(1L)
        verify(generationStateService).markFailed(1L)
        verify(generationStateService, never()).markCompleted(eq(1L), anyBoolean())
    }

    private companion object {
        const val TOP_N = 20
    }
}
