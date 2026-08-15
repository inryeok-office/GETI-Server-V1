package team.inreok.getiserver.domain.ai.event

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.willThrow
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.core.task.TaskExecutor
import team.inreok.getiserver.domain.ai.service.AiAnalysisService

@ExtendWith(MockitoExtension::class)
class AiAnalysisWorkEventListenerTest {
    @Mock
    private lateinit var aiAnalysisService: AiAnalysisService

    private val synchronousExecutor = TaskExecutor { it.run() }

    private val listener by lazy { AiAnalysisWorkEventListener(aiAnalysisService, synchronousExecutor) }

    @Test
    fun `Event를 받으면 실제 분석 처리를 호출한다`() {
        listener.onAiAnalysisRequested(AiAnalysisRequestedEvent(1L))

        verify(aiAnalysisService).processAnalysis(1L)
    }

    @Test
    fun `분석 처리 중 예외가 나도 밖으로 전파하지 않는다`() {
        willThrow(RuntimeException("boom")).given(aiAnalysisService).processAnalysis(1L)

        assertThat(catchThrowable { listener.onAiAnalysisRequested(AiAnalysisRequestedEvent(1L)) }).isNull()
    }
}
