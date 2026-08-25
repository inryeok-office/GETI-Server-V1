package team.inreok.getiserver.domain.search.event

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.willThrow
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.core.task.TaskExecutor
import team.inreok.getiserver.domain.ai.event.AiAnalysisCompletedEvent
import team.inreok.getiserver.domain.search.service.JobIndexSyncService

@ExtendWith(MockitoExtension::class)
class AiAnalysisIndexSyncEventListenerTest {
    @Mock
    private lateinit var jobIndexSyncService: JobIndexSyncService

    private val synchronousExecutor = TaskExecutor { it.run() }

    private val listener by lazy { AiAnalysisIndexSyncEventListener(jobIndexSyncService, synchronousExecutor) }

    @Test
    fun `Event를 받으면 해당 공고 1건만 증분 동기화한다`() {
        listener.onAiAnalysisCompleted(AiAnalysisCompletedEvent(1L))

        verify(jobIndexSyncService).syncOne(1L)
    }

    @Test
    fun `동기화 중 예외가 나도 밖으로 전파하지 않는다`() {
        willThrow(RuntimeException("boom")).given(jobIndexSyncService).syncOne(1L)

        assertThat(catchThrowable { listener.onAiAnalysisCompleted(AiAnalysisCompletedEvent(1L)) }).isNull()
    }
}
