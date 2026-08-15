package team.inreok.getiserver.domain.ai.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.ai.entity.JobAiAnalysis
import team.inreok.getiserver.domain.ai.entity.type.AiDifficulty
import team.inreok.getiserver.domain.ai.entity.type.AiFitLevel
import team.inreok.getiserver.domain.ai.entity.type.AiStatus
import team.inreok.getiserver.domain.ai.repository.JobAiAnalysisRepository
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class AiAnalysisSearchQueryPortImplTest {
    @Mock
    private lateinit var repository: JobAiAnalysisRepository

    private val objectMapper = ObjectMapper()

    private val port by lazy { AiAnalysisSearchQueryPortImpl(repository, objectMapper) }

    @Test
    fun `빈 Collection을 넘기면 Repository를 호출하지 않고 빈 Map을 반환한다`() {
        val result = port.findCompletedByJobIds(emptyList())

        assertThat(result).isEmpty()
        org.mockito.Mockito.verifyNoInteractions(repository)
    }

    @Test
    fun `COMPLETED가 아닌 분석은 결과 Map에서 제외한다`() {
        given(repository.findAllById(listOf(1L, 2L))).willReturn(
            listOf(
                completedAnalysis(jobId = 1L),
                JobAiAnalysis(
                    jobId = 2L,
                    status = AiStatus.PROCESSING,
                    reanalysisCount = 0,
                    requestedAt = LocalDateTime.now(),
                ),
            ),
        )

        val result = port.findCompletedByJobIds(listOf(1L, 2L))

        assertThat(result).containsOnlyKeys(1L)
    }

    @Test
    fun `재분석이 실패해도 이전 COMPLETED 값이 남아 있으면 상태가 FAILED이므로 제외한다`() {
        // AiAnalysisTransitionServiceImpl.markFailed는 이전에 채워진 summary/skills 등을 지우지
        // 않는다 -- 이 계약이 status로만 걸러야 하는 이유(Class KDoc 참고).
        val failedAfterCompleted =
            completedAnalysis(jobId = 1L).apply { status = AiStatus.FAILED }
        given(repository.findAllById(listOf(1L))).willReturn(listOf(failedAfterCompleted))

        val result = port.findCompletedByJobIds(listOf(1L))

        assertThat(result).isEmpty()
    }

    @Test
    fun `COMPLETED 분석은 GETI Tech Stack과 매칭된 ID만 담고 매칭되지 않은 기술은 제외한다`() {
        given(repository.findAllById(listOf(1L))).willReturn(listOf(completedAnalysis(jobId = 1L)))

        val snapshot = port.findCompletedByJobIds(listOf(1L))[1L]!!

        assertThat(snapshot.requiredTechStackIds).containsExactly(10L)
        assertThat(snapshot.preferredTechStackIds).isEmpty()
        assertThat(snapshot.highSchoolGraduateFit).isEqualTo("SUITABLE")
        assertThat(snapshot.entryLevelFit).isEqualTo("SUITABLE")
        assertThat(snapshot.difficulty).isEqualTo("NORMAL")
    }

    private fun completedAnalysis(jobId: Long) =
        JobAiAnalysis(
            jobId = jobId,
            status = AiStatus.COMPLETED,
            reanalysisCount = 0,
            requestedAt = LocalDateTime.now(),
        ).apply {
            requiredSkills = """[{"techStackId":10,"name":"Spring Boot"}]"""
            preferredSkills = """[{"techStackId":null,"name":"Docker"}]"""
            highSchoolGraduateFit = AiFitLevel.SUITABLE
            entryLevelFit = AiFitLevel.SUITABLE
            difficulty = AiDifficulty.NORMAL
        }
}
