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
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class JobAiAnalysisAccessorImplTest {
    @Mock
    private lateinit var repository: JobAiAnalysisRepository

    private val objectMapper = ObjectMapper()

    private val accessor by lazy { JobAiAnalysisAccessorImpl(repository, objectMapper) }

    @Test
    fun `분석이 시작되지 않았으면 null을 반환한다`() {
        given(repository.findById(1L)).willReturn(Optional.empty())

        assertThat(accessor.findSnapshot(1L)).isNull()
    }

    @Test
    fun `완료된 분석은 기술 목록과 canReanalyze를 계산해서 돌려준다`() {
        val analysis =
            JobAiAnalysis(
                jobId = 1L,
                status = AiStatus.COMPLETED,
                reanalysisCount = 1,
                requestedAt = LocalDateTime.now(),
            ).apply {
                summary = "요약"
                requiredSkills = """[{"techStackId":10,"name":"Spring Boot"}]"""
                preferredSkills = """[{"techStackId":null,"name":"Docker"}]"""
                highSchoolGraduateFit = AiFitLevel.SUITABLE
                entryLevelFit = AiFitLevel.SUITABLE
                difficulty = AiDifficulty.NORMAL
                completedAt = LocalDateTime.of(2026, 8, 15, 10, 0)
            }
        given(repository.findById(1L)).willReturn(Optional.of(analysis))

        val snapshot = accessor.findSnapshot(1L)!!

        assertThat(snapshot.status).isEqualTo("COMPLETED")
        assertThat(snapshot.isReanalysis).isTrue()
        assertThat(snapshot.requiredSkills).hasSize(1)
        assertThat(snapshot.requiredSkills[0].techStackId).isEqualTo(10L)
        assertThat(snapshot.preferredSkills[0].techStackId).isNull()
        assertThat(snapshot.canReanalyze).isTrue()
        assertThat(snapshot.remainingReanalysisCount).isEqualTo(2)
        assertThat(snapshot.analyzedAt).isEqualTo(LocalDateTime.of(2026, 8, 15, 10, 0))
    }

    @Test
    fun `PROCESSING이면 재분석할 수 없고 analyzedAt은 null이다`() {
        val analysis =
            JobAiAnalysis(
                jobId = 1L,
                status = AiStatus.PROCESSING,
                reanalysisCount = 0,
                requestedAt = LocalDateTime.now(),
            )
        given(repository.findById(1L)).willReturn(Optional.of(analysis))

        val snapshot = accessor.findSnapshot(1L)!!

        assertThat(snapshot.canReanalyze).isFalse()
        assertThat(snapshot.analyzedAt).isNull()
        assertThat(snapshot.requiredSkills).isEmpty()
    }

    @Test
    fun `PENDING이면 이미 대기열에 있는 상태라 재분석할 수 없다`() {
        // AiAnalysisTransitionServiceImpl.prepareReanalysis는 PENDING도 PROCESSING과 함께
        // 409로 거부한다 -- 조회 응답의 canReanalyze도 같은 기준이어야 한다(Code Review
        // 지적 사항, Issue #132).
        val analysis =
            JobAiAnalysis(jobId = 1L, status = AiStatus.PENDING, reanalysisCount = 0, requestedAt = LocalDateTime.now())
        given(repository.findById(1L)).willReturn(Optional.of(analysis))

        val snapshot = accessor.findSnapshot(1L)!!

        assertThat(snapshot.canReanalyze).isFalse()
    }

    @Test
    fun `3회를 모두 사용했으면 재분석할 수 없다`() {
        val analysis =
            JobAiAnalysis(jobId = 1L, status = AiStatus.FAILED, reanalysisCount = 3, requestedAt = LocalDateTime.now())
        given(repository.findById(1L)).willReturn(Optional.of(analysis))

        val snapshot = accessor.findSnapshot(1L)!!

        assertThat(snapshot.canReanalyze).isFalse()
        assertThat(snapshot.remainingReanalysisCount).isEqualTo(0)
    }
}
