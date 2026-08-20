package team.inreok.getiserver.domain.recommendation.service.impl

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anySet
import org.mockito.BDDMockito.given
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import team.inreok.getiserver.domain.ai.query.AiAnalysisSearchQueryPort
import team.inreok.getiserver.domain.ai.query.AiAnalysisSearchSnapshot
import team.inreok.getiserver.domain.company.query.CompanyQuery
import team.inreok.getiserver.domain.job.access.JobAiAnalysisAccessor
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.PostingType
import team.inreok.getiserver.domain.job.query.JobRecommendationCandidateQueryPort
import team.inreok.getiserver.domain.job.query.JobRecommendationCandidateSnapshot
import team.inreok.getiserver.domain.member.query.MemberApplicantSnapshotQueryPort
import team.inreok.getiserver.domain.recommendation.entity.MemberJobPreference
import team.inreok.getiserver.domain.recommendation.entity.type.ExclusionType
import team.inreok.getiserver.domain.recommendation.repository.MemberJobPreferenceRepository
import team.inreok.getiserver.domain.recommendation.repository.RecommendationGenerationStateRepository
import team.inreok.getiserver.domain.recommendation.repository.RecommendationPreferenceRepository
import team.inreok.getiserver.domain.recommendation.repository.RecommendationRepository
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * [RecommendationServiceImpl.registerExclusion]의 SIMILAR_JOBS 등록 즉시 반영을 검증한다(R6,
 * Issue #167 §5). `RecommendationServiceImplTest`와 나눠 detekt `LargeClass`(단일 Test Class가
 * 너무 커지는 것)를 넘지 않게 한다 -- 같은 Class를 하나 더 나눈 형태라 Mock/Fixture 구성은 그대로
 * 가져온다.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecommendationServiceImplSimilarJobsTest {
    @Mock
    private lateinit var recommendationRepository: RecommendationRepository

    @Mock
    private lateinit var recommendationPreferenceRepository: RecommendationPreferenceRepository

    @Mock
    private lateinit var recommendationGenerationStateRepository: RecommendationGenerationStateRepository

    @Mock
    private lateinit var memberJobPreferenceRepository: MemberJobPreferenceRepository

    @Mock
    private lateinit var jobRecommendationCandidateQueryPort: JobRecommendationCandidateQueryPort

    @Mock
    private lateinit var companyQuery: CompanyQuery

    @Mock
    private lateinit var memberApplicantSnapshotQueryPort: MemberApplicantSnapshotQueryPort

    @Mock
    private lateinit var aiAnalysisSearchQueryPort: AiAnalysisSearchQueryPort

    @Mock
    private lateinit var jobAiAnalysisAccessor: JobAiAnalysisAccessor

    @Captor
    private lateinit var preferenceCaptor: ArgumentCaptor<MemberJobPreference>

    private val service by lazy {
        RecommendationServiceImpl(
            recommendationRepository,
            recommendationPreferenceRepository,
            recommendationGenerationStateRepository,
            memberJobPreferenceRepository,
            jobRecommendationCandidateQueryPort,
            companyQuery,
            memberApplicantSnapshotQueryPort,
            JsonMapper(),
            "0 0 3 * * *",
            0.6,
            aiAnalysisSearchQueryPort,
            jobAiAnalysisAccessor,
        )
    }

    private val today: LocalDate = LocalDate.now()

    private fun jobOf(jobId: Long = 1L) =
        JobRecommendationCandidateSnapshot(
            jobId = jobId,
            companyId = 100L,
            title = "백엔드 개발 인턴",
            status = "PUBLISHED",
            targetGrade = 3,
            publishedAt = LocalDateTime.of(2026, 8, 1, 0, 0),
            recruitmentEndedAt = LocalDateTime.of(2026, 12, 31, 0, 0),
            postingType = PostingType.GENERAL,
            applicationMethod = ApplicationMethod.INTERNAL,
            viewCount = 10,
        )

    private fun aiSnapshotOf(requiredTechStackIds: List<Long> = emptyList()) =
        AiAnalysisSearchSnapshot(
            requiredTechStackIds = requiredTechStackIds,
            preferredTechStackIds = emptyList(),
            highSchoolGraduateFit = "SUITABLE",
            entryLevelFit = "SUITABLE",
            difficulty = "NORMAL",
        )

    private fun stubRegisterableJob(jobId: Long = 1L) {
        given(jobRecommendationCandidateQueryPort.findAllByIds(setOf(jobId))).willReturn(mapOf(jobId to jobOf(jobId)))
        given(memberJobPreferenceRepository.existsByMemberIdAndJobIdAndExclusionIsNotNull(1L, jobId)).willReturn(false)
        given(memberJobPreferenceRepository.findByMemberIdAndJobId(1L, jobId)).willReturn(null)
        given(memberJobPreferenceRepository.saveAndFlush(preferenceCaptor.capture())).willAnswer { invocation ->
            (invocation.arguments[0] as MemberJobPreference).apply { id = 1L }
        }
    }

    @Test
    fun `SIMILAR_JOBS 등록 시 유사 판정되는 오늘자 Recommendation을 즉시 삭제한다`() {
        stubRegisterableJob()
        given(recommendationRepository.findJobIdsByMemberIdAndRecommendationDate(1L, today)).willReturn(listOf(2L, 3L))
        given(aiAnalysisSearchQueryPort.findCompletedByJobIds(setOf(2L, 3L, 1L))).willReturn(
            mapOf(
                1L to aiSnapshotOf(listOf(1L, 2L, 3L)),
                // job 2는 기준 Job(1)과 완전히 같은 기술 스택 -> 유사도 1.0, 삭제 대상
                2L to aiSnapshotOf(listOf(1L, 2L, 3L)),
                // job 3은 기준 Job과 겹치는 기술이 없음 -> 유사도 0.0, 유지
                3L to aiSnapshotOf(listOf(100L, 101L)),
            ),
        )

        service.registerExclusion(1L, 1L, ExclusionType.SIMILAR_JOBS)

        verify(recommendationRepository).deleteAllByMemberIdAndJobIdIn(1L, setOf(2L))
    }

    @Test
    fun `SIMILAR_JOBS 등록 시 유사하지 않은 오늘자 Recommendation은 삭제하지 않는다`() {
        stubRegisterableJob()
        given(recommendationRepository.findJobIdsByMemberIdAndRecommendationDate(1L, today)).willReturn(listOf(2L))
        given(aiAnalysisSearchQueryPort.findCompletedByJobIds(setOf(2L, 1L))).willReturn(
            mapOf(
                1L to aiSnapshotOf(listOf(1L, 2L, 3L)),
                2L to aiSnapshotOf(listOf(100L, 101L)),
            ),
        )

        service.registerExclusion(1L, 1L, ExclusionType.SIMILAR_JOBS)

        verify(recommendationRepository, never()).deleteAllByMemberIdAndJobIdIn(anyLong(), anySet())
    }

    @Test
    fun `THIS_JOB 등록은 유사 확장 삭제를 수행하지 않는다`() {
        stubRegisterableJob()

        service.registerExclusion(1L, 1L, ExclusionType.THIS_JOB)

        verifyNoInteractions(aiAnalysisSearchQueryPort)
        verify(recommendationRepository, never()).findJobIdsByMemberIdAndRecommendationDate(1L, today)
        verify(recommendationRepository, never()).deleteAllByMemberIdAndJobIdIn(anyLong(), anySet())
    }

    @Test
    fun `SIMILAR_JOBS 등록 시 기준 Job의 AI 분석이 없으면 유사 확장 삭제를 하지 않는다`() {
        stubRegisterableJob()
        given(recommendationRepository.findJobIdsByMemberIdAndRecommendationDate(1L, today)).willReturn(listOf(2L))
        // 기준 Job(1)의 AI 분석이 COMPLETED가 아니라 결과 Map에 없다.
        given(aiAnalysisSearchQueryPort.findCompletedByJobIds(setOf(2L, 1L))).willReturn(
            mapOf(2L to aiSnapshotOf(listOf(1L, 2L, 3L))),
        )

        service.registerExclusion(1L, 1L, ExclusionType.SIMILAR_JOBS)

        verify(recommendationRepository, never()).deleteAllByMemberIdAndJobIdIn(anyLong(), anySet())
    }

    @Test
    fun `SIMILAR_JOBS 등록 시 오늘자 Recommendation이 없으면 유사 확장 삭제를 시도하지 않는다`() {
        stubRegisterableJob()
        given(recommendationRepository.findJobIdsByMemberIdAndRecommendationDate(1L, today)).willReturn(emptyList())

        service.registerExclusion(1L, 1L, ExclusionType.SIMILAR_JOBS)

        verifyNoInteractions(aiAnalysisSearchQueryPort)
        verify(recommendationRepository, never()).deleteAllByMemberIdAndJobIdIn(anyLong(), anySet())
    }

    @Test
    fun `SIMILAR_JOBS 등록 시 다른 회원의 오늘자 Recommendation에는 영향이 없다`() {
        // findJobIdsByMemberIdAndRecommendationDate/deleteAllByMemberIdAndJobIdIn 둘 다 memberId로
        // 좁혀 호출한다 -- 다른 회원(2L)의 Row는애초에 조회 대상에 포함되지 않는다.
        stubRegisterableJob()
        given(recommendationRepository.findJobIdsByMemberIdAndRecommendationDate(1L, today)).willReturn(listOf(2L))
        given(aiAnalysisSearchQueryPort.findCompletedByJobIds(setOf(2L, 1L))).willReturn(
            mapOf(1L to aiSnapshotOf(listOf(1L, 2L, 3L)), 2L to aiSnapshotOf(listOf(1L, 2L, 3L))),
        )

        service.registerExclusion(1L, 1L, ExclusionType.SIMILAR_JOBS)

        verify(recommendationRepository, never()).findJobIdsByMemberIdAndRecommendationDate(2L, today)
        verify(recommendationRepository, never()).deleteAllByMemberIdAndJobIdIn(2L, setOf(2L))
        verify(recommendationRepository).deleteAllByMemberIdAndJobIdIn(1L, setOf(2L))
    }
}
