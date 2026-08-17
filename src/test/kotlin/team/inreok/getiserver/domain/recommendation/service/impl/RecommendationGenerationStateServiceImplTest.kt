package team.inreok.getiserver.domain.recommendation.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.eq
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.recommendation.repository.RecommendationGenerationStateRepository
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class RecommendationGenerationStateServiceImplTest {
    @Mock
    private lateinit var repository: RecommendationGenerationStateRepository

    @Captor
    private lateinit var generatedAtCaptor: ArgumentCaptor<LocalDateTime>

    @Captor
    private lateinit var failedAtCaptor: ArgumentCaptor<LocalDateTime>

    private val service by lazy { RecommendationGenerationStateServiceImpl(repository) }

    @Test
    fun `markGenerating은 Repository의 GENERATING Upsert를 그대로 호출한다`() {
        service.markGenerating(1L)

        verify(repository).markGenerating(1L)
    }

    @Test
    fun `markCompleted는 결과가 있으면 READY로 기록한다`() {
        service.markCompleted(1L, hasResults = true)

        verify(repository).markCompleted(
            eq(1L),
            eq("READY") ?: "",
            generatedAtCaptor.capture() ?: LocalDateTime.now(),
        )
        assertThat(generatedAtCaptor.value).isNotNull()
    }

    @Test
    fun `markCompleted는 결과가 없으면 EMPTY로 기록한다`() {
        service.markCompleted(1L, hasResults = false)

        verify(repository).markCompleted(
            eq(1L),
            eq("EMPTY") ?: "",
            generatedAtCaptor.capture() ?: LocalDateTime.now(),
        )
        assertThat(generatedAtCaptor.value).isNotNull()
    }

    @Test
    fun `markFailed는 Repository의 FAILED Upsert를 실패 시각과 함께 호출한다`() {
        service.markFailed(1L)

        verify(repository).markFailed(eq(1L), failedAtCaptor.capture() ?: LocalDateTime.now())
        assertThat(failedAtCaptor.value).isNotNull()
    }
}
