package team.inreok.getiserver.domain.ai.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.dao.DataIntegrityViolationException
import team.inreok.getiserver.domain.ai.entity.JobAiAnalysis
import team.inreok.getiserver.domain.ai.entity.type.AiDifficulty
import team.inreok.getiserver.domain.ai.entity.type.AiFitLevel
import team.inreok.getiserver.domain.ai.entity.type.AiStatus
import team.inreok.getiserver.domain.ai.exception.AiAlreadyProcessingException
import team.inreok.getiserver.domain.ai.exception.AiReanalysisLimitExceededException
import team.inreok.getiserver.domain.ai.provider.AiAnalysisOutput
import team.inreok.getiserver.domain.ai.repository.JobAiAnalysisRepository
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class AiAnalysisTransitionServiceImplTest {
    @Mock
    private lateinit var repository: JobAiAnalysisRepository

    @Captor
    private lateinit var analysisCaptor: ArgumentCaptor<JobAiAnalysis>

    private val service by lazy { AiAnalysisTransitionServiceImpl(repository) }

    // --- createPendingIfAbsent ---

    @Test
    fun `분석 Row가 없으면 PENDING으로 새로 만들고 true를 반환한다`() {
        given(repository.existsById(1L)).willReturn(false)

        val created = service.createPendingIfAbsent(1L)

        assertThat(created).isTrue()
        verify(repository).saveAndFlush(analysisCaptor.capture() ?: newAnalysis())
        assertThat(analysisCaptor.value.jobId).isEqualTo(1L)
        assertThat(analysisCaptor.value.status).isEqualTo(AiStatus.PENDING)
    }

    @Test
    fun `분석 Row가 이미 있으면 아무 것도 하지 않고 false를 반환한다`() {
        given(repository.existsById(1L)).willReturn(true)

        val created = service.createPendingIfAbsent(1L)

        assertThat(created).isFalse()
        verify(repository, never()).saveAndFlush(anyAnalysis())
    }

    @Test
    fun `공유 PK 충돌이 나면 이미 있던 것으로 취급하고 false를 반환한다`() {
        given(repository.existsById(1L)).willReturn(false)
        given(repository.saveAndFlush(anyAnalysis())).willThrow(DataIntegrityViolationException("dup"))

        assertThat(service.createPendingIfAbsent(1L)).isFalse()
    }

    // --- prepareReanalysis ---

    @Test
    fun `분석 이력이 없으면 최초 분석으로 취급하고 재분석 횟수를 소비하지 않는다`() {
        given(repository.findByIdForUpdate(1L)).willReturn(null)

        val result = service.prepareReanalysis(1L)

        assertThat(result.isReanalysis).isFalse()
        assertThat(result.reanalysisCount).isEqualTo(0)
        assertThat(result.remainingReanalysisCount).isEqualTo(3)
        // 방금 PENDING으로 접수했으므로 지금 바로 다시 요청하면 409다(prepareReanalysis의
        // PENDING 거부 조건과 canReanalyze가 같은 기준을 공유해야 한다, Code Review 지적 사항).
        assertThat(result.canReanalyze).isFalse()
        assertThat(result.status).isEqualTo(AiStatus.PENDING)
    }

    @Test
    fun `이미 분석이 진행 중이면 재분석 요청을 거부한다`() {
        given(repository.findByIdForUpdate(1L)).willReturn(newAnalysis(status = AiStatus.PROCESSING))

        assertThatThrownBy { service.prepareReanalysis(1L) }
            .isInstanceOf(AiAlreadyProcessingException::class.java)
    }

    @Test
    fun `이미 대기열(PENDING)에 있으면 재분석 요청을 거부한다`() {
        // Worker가 아직 집어가지 않은 짧은 틈에 중복 요청이 들어와도 재분석 횟수를 중복
        // 소비하지 않아야 한다.
        given(repository.findByIdForUpdate(1L)).willReturn(newAnalysis(status = AiStatus.PENDING))

        assertThatThrownBy { service.prepareReanalysis(1L) }
            .isInstanceOf(AiAlreadyProcessingException::class.java)
    }

    @Test
    fun `재분석 횟수를 모두 사용했으면 거부한다`() {
        given(repository.findByIdForUpdate(1L))
            .willReturn(newAnalysis(status = AiStatus.COMPLETED, reanalysisCount = 3))

        assertThatThrownBy { service.prepareReanalysis(1L) }
            .isInstanceOf(AiReanalysisLimitExceededException::class.java)
    }

    @Test
    fun `재분석을 접수하면 횟수를 1 늘리고 PENDING으로 전이한다`() {
        given(repository.findByIdForUpdate(1L))
            .willReturn(newAnalysis(status = AiStatus.COMPLETED, reanalysisCount = 1))

        val result = service.prepareReanalysis(1L)

        assertThat(result.isReanalysis).isTrue()
        assertThat(result.reanalysisCount).isEqualTo(2)
        assertThat(result.remainingReanalysisCount).isEqualTo(1)
        assertThat(result.canReanalyze).isFalse()
        verify(repository).save(analysisCaptor.capture() ?: newAnalysis())
        assertThat(analysisCaptor.value.status).isEqualTo(AiStatus.PENDING)
        assertThat(analysisCaptor.value.errorMessage).isNull()
    }

    // --- claimForProcessing ---

    @Test
    fun `PENDING이면 PROCESSING으로 전이하고 true를 반환한다`() {
        given(repository.findByIdForUpdate(1L)).willReturn(newAnalysis(status = AiStatus.PENDING))

        val claimed = service.claimForProcessing(1L)

        assertThat(claimed).isTrue()
        verify(repository).save(analysisCaptor.capture() ?: newAnalysis())
        assertThat(analysisCaptor.value.status).isEqualTo(AiStatus.PROCESSING)
    }

    @Test
    fun `PENDING이 아니면 전이하지 않고 false를 반환한다(중복 처리 방지)`() {
        given(repository.findByIdForUpdate(1L)).willReturn(newAnalysis(status = AiStatus.PROCESSING))

        val claimed = service.claimForProcessing(1L)

        assertThat(claimed).isFalse()
        verify(repository, never()).save(anyAnalysis())
    }

    @Test
    fun `분석 Row가 없으면 false를 반환한다`() {
        given(repository.findByIdForUpdate(1L)).willReturn(null)

        assertThat(service.claimForProcessing(1L)).isFalse()
    }

    // --- markCompleted / markFailed ---

    @Test
    fun `완료 처리하면 결과 Field를 채우고 오류 메시지를 지운다`() {
        given(repository.findByIdForUpdate(1L)).willReturn(newAnalysis(status = AiStatus.PROCESSING))
        val output =
            AiAnalysisOutput(
                summary = "요약",
                requiredSkillNames = listOf("Spring Boot"),
                preferredSkillNames = emptyList(),
                highSchoolGraduateFit = AiFitLevel.SUITABLE,
                entryLevelFit = AiFitLevel.SUITABLE,
                difficulty = AiDifficulty.NORMAL,
                model = "gpt-4o-mini",
            )

        service.markCompleted(1L, output, "[]", "[]", "JOB_ANALYSIS_V1", 1)

        verify(repository).save(analysisCaptor.capture() ?: newAnalysis())
        val saved = analysisCaptor.value
        assertThat(saved.status).isEqualTo(AiStatus.COMPLETED)
        assertThat(saved.summary).isEqualTo("요약")
        assertThat(saved.provider).isEqualTo("OPENAI")
        assertThat(saved.model).isEqualTo("gpt-4o-mini")
        assertThat(saved.promptVersion).isEqualTo("JOB_ANALYSIS_V1")
        assertThat(saved.analysisVersion).isEqualTo(1)
        assertThat(saved.errorMessage).isNull()
        assertThat(saved.completedAt).isNotNull()
    }

    @Test
    fun `실패 처리하면 FAILED로 전이하고 안전한 오류 메시지를 남긴다`() {
        given(repository.findByIdForUpdate(1L)).willReturn(newAnalysis(status = AiStatus.PROCESSING))

        service.markFailed(1L, "OpenAI 인증에 실패했습니다.")

        verify(repository).save(analysisCaptor.capture() ?: newAnalysis())
        assertThat(analysisCaptor.value.status).isEqualTo(AiStatus.FAILED)
        assertThat(analysisCaptor.value.errorMessage).isEqualTo("OpenAI 인증에 실패했습니다.")
        assertThat(analysisCaptor.value.completedAt).isNotNull()
    }

    private fun newAnalysis(
        status: AiStatus = AiStatus.PENDING,
        reanalysisCount: Int = 0,
    ) = JobAiAnalysis(jobId = 1L, status = status, reanalysisCount = reanalysisCount, requestedAt = LocalDateTime.now())

    private fun anyAnalysis(): JobAiAnalysis =
        org.mockito.ArgumentMatchers.any(JobAiAnalysis::class.java) ?: newAnalysis()
}
