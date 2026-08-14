package team.inreok.getiserver.domain.ai.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.springframework.context.ApplicationEventPublisher
import team.inreok.getiserver.domain.ai.entity.type.AiDifficulty
import team.inreok.getiserver.domain.ai.entity.type.AiFitLevel
import team.inreok.getiserver.domain.ai.entity.type.AiStatus
import team.inreok.getiserver.domain.ai.event.AiAnalysisRequestedEvent
import team.inreok.getiserver.domain.ai.exception.AiJobNotFoundException
import team.inreok.getiserver.domain.ai.provider.AiAnalysisInput
import team.inreok.getiserver.domain.ai.provider.AiAnalysisOutput
import team.inreok.getiserver.domain.ai.provider.AiAnalysisProvider
import team.inreok.getiserver.domain.ai.provider.AiAnalysisProviderException
import team.inreok.getiserver.domain.ai.service.AiAnalysisTransitionService
import team.inreok.getiserver.domain.ai.service.AiReanalysisPreparation
import team.inreok.getiserver.domain.job.query.JobAiAnalysisInputQueryPort
import team.inreok.getiserver.domain.job.query.JobAiAnalysisInputSnapshot
import team.inreok.getiserver.domain.member.query.TechStackMatchQueryPort
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime

// Kotlin non-null 파라미터에 bare any()를 쓰면 null이 반환되어 NPE가 나므로, 이 파일 전체에서
// Type을 명시한 any(SomeClass::class.java) ?: 기본값 형태를 쓴다(JobServiceTest와 같은 이유).
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiAnalysisServiceImplTest {
    @Mock
    private lateinit var transitionService: AiAnalysisTransitionService

    @Mock
    private lateinit var jobAiAnalysisInputQueryPort: JobAiAnalysisInputQueryPort

    @Mock
    private lateinit var aiAnalysisProvider: AiAnalysisProvider

    @Mock
    private lateinit var techStackMatchQueryPort: TechStackMatchQueryPort

    @Mock
    private lateinit var eventPublisher: ApplicationEventPublisher

    private val objectMapper = ObjectMapper()

    private val service by lazy {
        AiAnalysisServiceImpl(
            transitionService,
            jobAiAnalysisInputQueryPort,
            aiAnalysisProvider,
            techStackMatchQueryPort,
            objectMapper,
            eventPublisher,
        )
    }

    // --- triggerInitialAnalysisIfAbsent ---

    @Test
    fun `분석 Row를 새로 만들었을 때만 처리 Event를 발행한다`() {
        given(transitionService.createPendingIfAbsent(1L)).willReturn(true)

        service.triggerInitialAnalysisIfAbsent(1L)

        verifyEventPublished()
    }

    @Test
    fun `이미 분석 Row가 있으면 Event를 발행하지 않는다`() {
        given(transitionService.createPendingIfAbsent(1L)).willReturn(false)

        service.triggerInitialAnalysisIfAbsent(1L)

        verify(eventPublisher, never()).publishEvent(anyEvent())
    }

    // --- reanalyze ---

    @Test
    fun `게시되지 않았거나 없는 공고를 재분석하면 AiJobNotFoundException을 던진다`() {
        given(jobAiAnalysisInputQueryPort.findById(1L)).willReturn(null)

        assertThatThrownBy { service.reanalyze(1L) }.isInstanceOf(AiJobNotFoundException::class.java)
        verify(transitionService, never()).prepareReanalysis(anyLong())
    }

    @Test
    fun `DRAFT 공고를 재분석하면 AiJobNotFoundException을 던진다`() {
        given(jobAiAnalysisInputQueryPort.findById(1L)).willReturn(inputSnapshot(status = "DRAFT"))

        assertThatThrownBy { service.reanalyze(1L) }.isInstanceOf(AiJobNotFoundException::class.java)
    }

    @Test
    fun `게시된 공고를 재분석하면 접수하고 Event를 발행한다`() {
        given(jobAiAnalysisInputQueryPort.findById(1L)).willReturn(inputSnapshot())
        given(transitionService.prepareReanalysis(1L)).willReturn(
            AiReanalysisPreparation(
                status = AiStatus.PENDING,
                isReanalysis = true,
                reanalysisCount = 1,
                remainingReanalysisCount = 2,
                canReanalyze = true,
                requestedAt = LocalDateTime.now(),
            ),
        )

        val response = service.reanalyze(1L)

        assertThat(response.jobId).isEqualTo(1L)
        assertThat(response.reanalysisCount).isEqualTo(1)
        verifyEventPublished()
    }

    // --- processAnalysis ---

    @Test
    fun `PENDING 상태를 선점하지 못하면 Provider를 호출하지 않는다(중복 처리 방지)`() {
        given(transitionService.claimForProcessing(1L)).willReturn(false)

        service.processAnalysis(1L)

        verify(aiAnalysisProvider, never()).analyze(anyInput())
    }

    @Test
    fun `분석 대상 공고를 찾을 수 없으면 실패로 기록한다`() {
        given(transitionService.claimForProcessing(1L)).willReturn(true)
        given(jobAiAnalysisInputQueryPort.findById(1L)).willReturn(null)

        service.processAnalysis(1L)

        verify(transitionService).markFailed(eq(1L), anyString())
        verify(aiAnalysisProvider, never()).analyze(anyInput())
    }

    @Test
    fun `Provider 성공 시 기술을 매칭하고 완료로 기록한다`() {
        given(transitionService.claimForProcessing(1L)).willReturn(true)
        given(jobAiAnalysisInputQueryPort.findById(1L)).willReturn(inputSnapshot())
        given(aiAnalysisProvider.analyze(anyInput())).willReturn(
            AiAnalysisOutput(
                summary = "요약",
                requiredSkillNames = listOf("Spring Boot", "Kotlin"),
                preferredSkillNames = listOf("Docker"),
                highSchoolGraduateFit = AiFitLevel.SUITABLE,
                entryLevelFit = AiFitLevel.SUITABLE,
                difficulty = AiDifficulty.NORMAL,
                model = "gpt-4o-mini",
            ),
        )
        given(techStackMatchQueryPort.matchByNames(listOf("Spring Boot", "Kotlin")))
            .willReturn(mapOf("Spring Boot" to 10L))
        given(techStackMatchQueryPort.matchByNames(listOf("Docker"))).willReturn(emptyMap())

        service.processAnalysis(1L)

        val jobIdCaptor = ArgumentCaptor.forClass(Long::class.java)
        val requiredJsonCaptor = ArgumentCaptor.forClass(String::class.java)
        val preferredJsonCaptor = ArgumentCaptor.forClass(String::class.java)
        val promptVersionCaptor = ArgumentCaptor.forClass(String::class.java)
        val analysisVersionCaptor = ArgumentCaptor.forClass(Int::class.java)
        verify(transitionService).markCompleted(
            jobIdCaptor.capture() ?: 0L,
            anyOutput(),
            requiredJsonCaptor.capture() ?: "",
            preferredJsonCaptor.capture() ?: "",
            promptVersionCaptor.capture() ?: "",
            analysisVersionCaptor.capture() ?: 0,
        )
        assertThat(jobIdCaptor.value).isEqualTo(1L)
        assertThat(requiredJsonCaptor.value).contains("\"techStackId\":10").contains("Spring Boot")
        assertThat(preferredJsonCaptor.value).contains("\"techStackId\":null").contains("Docker")
        assertThat(promptVersionCaptor.value).isEqualTo("JOB_ANALYSIS_V1")
        assertThat(analysisVersionCaptor.value).isEqualTo(1)
    }

    @Test
    fun `Provider가 실패하면 안전한 메시지로 실패 처리한다`() {
        given(transitionService.claimForProcessing(1L)).willReturn(true)
        given(jobAiAnalysisInputQueryPort.findById(1L)).willReturn(inputSnapshot())
        given(aiAnalysisProvider.analyze(anyInput()))
            .willThrow(AiAnalysisProviderException.AuthenticationFailed())

        service.processAnalysis(1L)

        verify(transitionService).markFailed(1L, "OpenAI 인증에 실패했습니다.")
    }

    private fun verifyEventPublished() {
        verify(eventPublisher).publishEvent(anyEvent())
    }

    private fun anyEvent(): Any =
        org.mockito.ArgumentMatchers.any(AiAnalysisRequestedEvent::class.java) ?: AiAnalysisRequestedEvent(1L)

    private fun anyInput(): AiAnalysisInput =
        org.mockito.ArgumentMatchers.any(AiAnalysisInput::class.java) ?: sampleInput()

    private fun anyOutput(): AiAnalysisOutput =
        org.mockito.ArgumentMatchers.any(AiAnalysisOutput::class.java) ?: sampleOutput()

    private fun sampleInput() =
        AiAnalysisInput(
            jobId = 1L,
            title = "",
            postingType = "MOU",
            applicationMethod = "EXTERNAL",
            companyName = null,
            content = null,
            startDate = null,
            endDate = null,
            targetGrade = null,
        )

    private fun sampleOutput() =
        AiAnalysisOutput(
            summary = "",
            requiredSkillNames = emptyList(),
            preferredSkillNames = emptyList(),
            highSchoolGraduateFit = AiFitLevel.SUITABLE,
            entryLevelFit = AiFitLevel.SUITABLE,
            difficulty = AiDifficulty.NORMAL,
            model = "",
        )

    private fun inputSnapshot(status: String = "PUBLISHED") =
        JobAiAnalysisInputSnapshot(
            jobId = 1L,
            title = "백엔드 개발자 채용",
            postingType = "MOU",
            applicationMethod = "EXTERNAL",
            status = status,
            companyName = "인력개발원",
            content = "## 모집 부문\n- 백엔드 개발자",
            startDate = null,
            endDate = null,
            targetGrade = 3,
        )
}
