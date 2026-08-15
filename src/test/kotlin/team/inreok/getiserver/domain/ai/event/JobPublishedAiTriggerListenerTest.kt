package team.inreok.getiserver.domain.ai.event

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.core.task.TaskExecutor
import team.inreok.getiserver.domain.ai.service.AiAnalysisService
import team.inreok.getiserver.domain.job.event.JobChangedEvent
import team.inreok.getiserver.domain.job.query.JobAiAnalysisInputQueryPort
import team.inreok.getiserver.domain.job.query.JobAiAnalysisInputSnapshot

@ExtendWith(MockitoExtension::class)
class JobPublishedAiTriggerListenerTest {
    @Mock
    private lateinit var jobAiAnalysisInputQueryPort: JobAiAnalysisInputQueryPort

    @Mock
    private lateinit var aiAnalysisService: AiAnalysisService

    // 실제 요청 Thread에서 즉시 실행되게 해 Test를 결정적으로 만든다(Executor를 실제 Thread Pool로
    // 쓰지 않음).
    private val synchronousExecutor = TaskExecutor { it.run() }

    private val listener by lazy {
        JobPublishedAiTriggerListener(jobAiAnalysisInputQueryPort, aiAnalysisService, synchronousExecutor)
    }

    @Test
    fun `PUBLISHED 공고면 AI 분석을 Trigger한다`() {
        given(jobAiAnalysisInputQueryPort.findById(1L)).willReturn(snapshot("PUBLISHED"))

        listener.onJobChanged(JobChangedEvent(1L))

        verify(aiAnalysisService).triggerInitialAnalysisIfAbsent(1L)
    }

    @Test
    fun `DRAFT 공고면 AI 분석을 Trigger하지 않는다`() {
        given(jobAiAnalysisInputQueryPort.findById(1L)).willReturn(snapshot("DRAFT"))

        listener.onJobChanged(JobChangedEvent(1L))

        verify(aiAnalysisService, never()).triggerInitialAnalysisIfAbsent(org.mockito.ArgumentMatchers.anyLong())
    }

    @Test
    fun `존재하지 않는 공고면 오류 없이 무시한다`() {
        given(jobAiAnalysisInputQueryPort.findById(1L)).willReturn(null)

        assertThat(catchThrowable { listener.onJobChanged(JobChangedEvent(1L)) }).isNull()
        verify(aiAnalysisService, never()).triggerInitialAnalysisIfAbsent(org.mockito.ArgumentMatchers.anyLong())
    }

    private fun catchThrowable(block: () -> Unit): Throwable? =
        org.assertj.core.api.Assertions
            .catchThrowable(block)

    private fun snapshot(status: String) =
        JobAiAnalysisInputSnapshot(
            jobId = 1L,
            title = "제목",
            postingType = "MOU",
            applicationMethod = "EXTERNAL",
            status = status,
            companyName = null,
            content = null,
            startDate = null,
            endDate = null,
            targetGrade = null,
        )
}
