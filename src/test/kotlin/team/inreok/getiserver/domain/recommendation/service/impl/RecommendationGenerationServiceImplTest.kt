package team.inreok.getiserver.domain.recommendation.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyCollection
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anySet
import org.mockito.ArgumentMatchers.eq
import org.mockito.BDDMockito.given
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.ai.query.AiAnalysisSearchQueryPort
import team.inreok.getiserver.domain.ai.query.AiAnalysisSearchSnapshot
import team.inreok.getiserver.domain.job.access.JobApplicationEligibilityAccessSnapshot
import team.inreok.getiserver.domain.job.access.JobApplicationEligibilityAccessor
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.PostingType
import team.inreok.getiserver.domain.job.query.JobRecommendationCandidateQueryPort
import team.inreok.getiserver.domain.job.query.JobRecommendationCandidateSnapshot
import team.inreok.getiserver.domain.member.query.RecommendationMemberProfileQueryPort
import team.inreok.getiserver.domain.member.query.RecommendationMemberProfileSnapshot
import team.inreok.getiserver.domain.recommendation.entity.Recommendation
import team.inreok.getiserver.domain.recommendation.entity.type.ExclusionType
import team.inreok.getiserver.domain.recommendation.repository.MemberJobPreferenceRepository
import team.inreok.getiserver.domain.recommendation.repository.RecommendationRepository
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class RecommendationGenerationServiceImplTest {
    @Mock
    private lateinit var memberProfileQueryPort: RecommendationMemberProfileQueryPort

    @Mock
    private lateinit var jobCandidateQueryPort: JobRecommendationCandidateQueryPort

    @Mock
    private lateinit var aiAnalysisSearchQueryPort: AiAnalysisSearchQueryPort

    @Mock
    private lateinit var jobApplicationEligibilityAccessor: JobApplicationEligibilityAccessor

    @Mock
    private lateinit var memberJobPreferenceRepository: MemberJobPreferenceRepository

    @Mock
    private lateinit var recommendationRepository: RecommendationRepository

    @Captor
    private lateinit var savedCaptor: ArgumentCaptor<List<Recommendation>>

    private val objectMapper = ObjectMapper()

    private val service by lazy {
        RecommendationGenerationServiceImpl(
            memberProfileQueryPort,
            jobCandidateQueryPort,
            aiAnalysisSearchQueryPort,
            jobApplicationEligibilityAccessor,
            memberJobPreferenceRepository,
            recommendationRepository,
            objectMapper,
            0.6,
        )
    }

    private fun memberOf(techStackIds: Set<Long> = setOf(1L)) =
        RecommendationMemberProfileSnapshot(memberId = 1L, status = "ACTIVE", grade = 3, techStackIds = techStackIds)

    private fun jobOf(id: Long) =
        JobRecommendationCandidateSnapshot(
            jobId = id,
            companyId = 100L,
            title = "Job $id",
            status = "PUBLISHED",
            targetGrade = 3,
            publishedAt = LocalDateTime.of(2026, 8, 1, 0, 0),
            recruitmentEndedAt = LocalDateTime.of(2026, 12, 31, 0, 0),
            postingType = PostingType.GENERAL,
            applicationMethod = ApplicationMethod.INTERNAL,
            viewCount = 0,
        )

    private fun aiSnapshotOf(requiredTechStackIds: List<Long> = listOf(1L)) =
        AiAnalysisSearchSnapshot(
            requiredTechStackIds = requiredTechStackIds,
            preferredTechStackIds = emptyList(),
            highSchoolGraduateFit = "SUITABLE",
            entryLevelFit = "SUITABLE",
            difficulty = "NORMAL",
        )

    private fun eligibilityOf(
        eligibilityReason: String = "AVAILABLE",
        applicationId: Long? = null,
    ) = JobApplicationEligibilityAccessSnapshot(
        canApply = eligibilityReason == "AVAILABLE",
        eligibilityReason = eligibilityReason,
        eligibilityMessage = "message",
        applicationId = applicationId,
        applicationStatus = if (applicationId != null) "SUBMITTED" else null,
        availableActions = emptyList(),
    )

    @Test
    fun `Member Profile을 찾을 수 없으면 아무것도 하지 않고 빈 목록을 반환한다`() {
        given(memberProfileQueryPort.findById(1L)).willReturn(null)

        val result = service.generateForMember(1L, limit = 10)

        assertThat(result).isEmpty()
        verifyNoInteractions(jobCandidateQueryPort, aiAnalysisSearchQueryPort, recommendationRepository)
    }

    @Test
    fun `Candidate가 100개여도 AI Query는 한 번만 호출한다(Batch, N+1 방지)`() {
        val jobs = (1..100L).map { jobOf(it) }
        given(memberProfileQueryPort.findById(1L)).willReturn(memberOf())
        given(jobCandidateQueryPort.findAllPublished()).willReturn(jobs)
        given(memberJobPreferenceRepository.findExcludedJobIdsByMemberId(1L)).willReturn(emptyList())
        given(aiAnalysisSearchQueryPort.findCompletedByJobIds(jobs.map { it.jobId })).willReturn(
            jobs.associate { it.jobId to aiSnapshotOf() },
        )
        given(jobApplicationEligibilityAccessor.findAllByJobIds(jobs.map { it.jobId }.toSet(), 1L)).willReturn(
            jobs.associate { it.jobId to eligibilityOf() },
        )

        service.generateForMember(1L, limit = 10)

        verify(aiAnalysisSearchQueryPort, times(1)).findCompletedByJobIds(anyCollection())
        verify(jobApplicationEligibilityAccessor, times(1)).findAllByJobIds(anySet(), eq(1L))
    }

    @Test
    fun `Hard Filter와 Score 계산을 통과한 후보만 저장하고 기존 오늘자 결과를 교체한다`() {
        val jobs = listOf(jobOf(1L), jobOf(2L))
        given(memberProfileQueryPort.findById(1L)).willReturn(memberOf())
        given(jobCandidateQueryPort.findAllPublished()).willReturn(jobs)
        given(memberJobPreferenceRepository.findExcludedJobIdsByMemberId(1L)).willReturn(emptyList())
        given(aiAnalysisSearchQueryPort.findCompletedByJobIds(jobs.map { it.jobId })).willReturn(
            mapOf(1L to aiSnapshotOf(), 2L to aiSnapshotOf()),
        )
        given(jobApplicationEligibilityAccessor.findAllByJobIds(jobs.map { it.jobId }.toSet(), 1L)).willReturn(
            mapOf(1L to eligibilityOf(), 2L to eligibilityOf()),
        )

        val result = service.generateForMember(1L, limit = 10)

        assertThat(result).hasSize(2)
        verify(recommendationRepository).deleteAllByMemberIdAndRecommendationDate(1L, LocalDate.now())
        verify(recommendationRepository).saveAll(savedCaptor.capture())
        val saved = savedCaptor.value
        assertThat(saved).hasSize(2)
        assertThat(saved.map { it.algorithmVersion }).containsOnly(RECOMMENDATION_ALGORITHM_VERSION)
        assertThat(saved.map { it.rank }).containsExactlyInAnyOrder(1, 2)
    }

    @Test
    fun `관심 없음 처리한 공고는 제외되고 남은 후보가 없으면 삭제만 하고 저장은 하지 않는다`() {
        val jobs = listOf(jobOf(1L))
        given(memberProfileQueryPort.findById(1L)).willReturn(memberOf())
        given(jobCandidateQueryPort.findAllPublished()).willReturn(jobs)
        given(memberJobPreferenceRepository.findExcludedJobIdsByMemberId(1L)).willReturn(listOf(1L))
        given(aiAnalysisSearchQueryPort.findCompletedByJobIds(jobs.map { it.jobId })).willReturn(
            mapOf(1L to aiSnapshotOf()),
        )
        given(jobApplicationEligibilityAccessor.findAllByJobIds(jobs.map { it.jobId }.toSet(), 1L)).willReturn(
            mapOf(1L to eligibilityOf()),
        )

        val result = service.generateForMember(1L, limit = 10)

        assertThat(result).isEmpty()
        verify(recommendationRepository).deleteAllByMemberIdAndRecommendationDate(1L, LocalDate.now())
        verify(recommendationRepository, never()).saveAll(anyList())
    }

    @Test
    fun `이미 북마크한 공고는 제외되고 남은 후보가 없으면 삭제만 하고 저장은 하지 않는다`() {
        val jobs = listOf(jobOf(1L))
        given(memberProfileQueryPort.findById(1L)).willReturn(memberOf())
        given(jobCandidateQueryPort.findAllPublished()).willReturn(jobs)
        given(memberJobPreferenceRepository.findExcludedJobIdsByMemberId(1L)).willReturn(emptyList())
        given(memberJobPreferenceRepository.findBookmarkedJobIdsByMemberId(1L)).willReturn(listOf(1L))
        given(aiAnalysisSearchQueryPort.findCompletedByJobIds(jobs.map { it.jobId })).willReturn(
            mapOf(1L to aiSnapshotOf()),
        )
        given(jobApplicationEligibilityAccessor.findAllByJobIds(jobs.map { it.jobId }.toSet(), 1L)).willReturn(
            mapOf(1L to eligibilityOf()),
        )

        val result = service.generateForMember(1L, limit = 10)

        assertThat(result).isEmpty()
        verify(recommendationRepository).deleteAllByMemberIdAndRecommendationDate(1L, LocalDate.now())
        verify(recommendationRepository, never()).saveAll(anyList())
    }

    @Test
    fun `이미 활성 지원서가 있는 공고는 제외되고 남은 후보가 없으면 삭제만 하고 저장은 하지 않는다`() {
        val jobs = listOf(jobOf(1L))
        given(memberProfileQueryPort.findById(1L)).willReturn(memberOf())
        given(jobCandidateQueryPort.findAllPublished()).willReturn(jobs)
        given(memberJobPreferenceRepository.findExcludedJobIdsByMemberId(1L)).willReturn(emptyList())
        given(aiAnalysisSearchQueryPort.findCompletedByJobIds(jobs.map { it.jobId })).willReturn(
            mapOf(1L to aiSnapshotOf()),
        )
        given(jobApplicationEligibilityAccessor.findAllByJobIds(jobs.map { it.jobId }.toSet(), 1L)).willReturn(
            mapOf(1L to eligibilityOf(eligibilityReason = "ALREADY_APPLIED", applicationId = 10L)),
        )

        val result = service.generateForMember(1L, limit = 10)

        assertThat(result).isEmpty()
        verify(recommendationRepository).deleteAllByMemberIdAndRecommendationDate(1L, LocalDate.now())
        verify(recommendationRepository, never()).saveAll(anyList())
    }

    @Test
    fun `연결 Form이 비활성화돼 eligibilityReason이 JOB_NOT_PUBLISHED로 나와도 activeApplication이 있으면 제외한다`() {
        // JobApplicationEligibility.computeEligibilityReason은 hasActiveApplication ->
        // ALREADY_APPLIED보다 !hasActiveLinkedForm -> JOB_NOT_PUBLISHED를 먼저 평가한다. 이미
        // 활성 지원서가 있어도 그 Job에 연결된 Form이 비활성화되면 eligibilityReason은
        // "ALREADY_APPLIED"가 아니라 "JOB_NOT_PUBLISHED"로 돌아온다 -- 이때도 applicationId는
        // activeApplication?.id로 그대로 채워지므로 hasActiveApplication은 여전히 true여야
        // 한다(코드리뷰 반영, PR #166).
        val jobs = listOf(jobOf(1L))
        given(memberProfileQueryPort.findById(1L)).willReturn(memberOf())
        given(jobCandidateQueryPort.findAllPublished()).willReturn(jobs)
        given(memberJobPreferenceRepository.findExcludedJobIdsByMemberId(1L)).willReturn(emptyList())
        given(aiAnalysisSearchQueryPort.findCompletedByJobIds(jobs.map { it.jobId })).willReturn(
            mapOf(1L to aiSnapshotOf()),
        )
        given(jobApplicationEligibilityAccessor.findAllByJobIds(jobs.map { it.jobId }.toSet(), 1L)).willReturn(
            mapOf(1L to eligibilityOf(eligibilityReason = "JOB_NOT_PUBLISHED", applicationId = 10L)),
        )

        val result = service.generateForMember(1L, limit = 10)

        assertThat(result).isEmpty()
        verify(recommendationRepository, never()).saveAll(anyList())
    }

    @Test
    fun `NOT_INTERNAL(외부 지원 공고)은 제외하지 않는다`() {
        val jobs = listOf(jobOf(1L))
        given(memberProfileQueryPort.findById(1L)).willReturn(memberOf())
        given(jobCandidateQueryPort.findAllPublished()).willReturn(jobs)
        given(memberJobPreferenceRepository.findExcludedJobIdsByMemberId(1L)).willReturn(emptyList())
        given(aiAnalysisSearchQueryPort.findCompletedByJobIds(jobs.map { it.jobId })).willReturn(
            mapOf(1L to aiSnapshotOf()),
        )
        // 외부 지원 공고는 canApply=false(NOT_INTERNAL)이지만, ALREADY_APPLIED가 아니므로
        // Recommendation Hard Filter가 배제하지 않아야 한다(canApply 전체를 신호로 쓰면 외부
        // 지원 공고가 전부 사라지는 회귀가 생긴다, Issue #165 PR 본문 참고).
        given(jobApplicationEligibilityAccessor.findAllByJobIds(jobs.map { it.jobId }.toSet(), 1L)).willReturn(
            mapOf(1L to eligibilityOf(eligibilityReason = "NOT_INTERNAL")),
        )

        val result = service.generateForMember(1L, limit = 10)

        assertThat(result).hasSize(1)
    }

    // ---------- SIMILAR_JOBS 유사 공고 확장 (R6, Issue #167) ----------

    @Test
    fun `SIMILAR_JOBS 기준 Job과 유사한 후보는 제외되고 유사하지 않은 후보는 유지된다`() {
        val jobs = listOf(jobOf(1L), jobOf(2L))
        given(memberProfileQueryPort.findById(1L)).willReturn(memberOf())
        given(jobCandidateQueryPort.findAllPublished()).willReturn(jobs)
        given(memberJobPreferenceRepository.findExcludedJobIdsByMemberId(1L)).willReturn(emptyList())
        given(memberJobPreferenceRepository.findJobIdsByMemberIdAndExclusionType(1L, ExclusionType.SIMILAR_JOBS))
            .willReturn(listOf(99L))
        given(aiAnalysisSearchQueryPort.findCompletedByJobIds(listOf(1L, 2L, 99L))).willReturn(
            mapOf(
                // job 1은 기준 Job(99)과 완전히 같은 기술 스택 -> 유사도 1.0, 제외
                1L to aiSnapshotOf(requiredTechStackIds = listOf(1L, 2L, 3L)),
                // job 2는 기준 Job과 겹치는 기술이 없음 -> 유사도 0.0, 유지
                2L to aiSnapshotOf(requiredTechStackIds = listOf(100L, 101L)),
                99L to aiSnapshotOf(requiredTechStackIds = listOf(1L, 2L, 3L)),
            ),
        )
        given(jobApplicationEligibilityAccessor.findAllByJobIds(jobs.map { it.jobId }.toSet(), 1L)).willReturn(
            mapOf(1L to eligibilityOf(), 2L to eligibilityOf()),
        )

        val result = service.generateForMember(1L, limit = 10)

        assertThat(result.map { it.jobId }).containsExactly(2L)
    }

    @Test
    fun `여러 SIMILAR_JOBS 기준 Job 중 하나라도 유사하면 후보를 제외한다`() {
        val jobs = listOf(jobOf(1L))
        given(memberProfileQueryPort.findById(1L)).willReturn(memberOf())
        given(jobCandidateQueryPort.findAllPublished()).willReturn(jobs)
        given(memberJobPreferenceRepository.findExcludedJobIdsByMemberId(1L)).willReturn(emptyList())
        given(memberJobPreferenceRepository.findJobIdsByMemberIdAndExclusionType(1L, ExclusionType.SIMILAR_JOBS))
            .willReturn(listOf(98L, 99L))
        given(aiAnalysisSearchQueryPort.findCompletedByJobIds(listOf(1L, 98L, 99L))).willReturn(
            mapOf(
                1L to aiSnapshotOf(requiredTechStackIds = listOf(1L, 2L, 3L)),
                // 98은 겹치지 않지만 99는 완전히 겹친다 -> 하나라도 유사하면 제외
                98L to aiSnapshotOf(requiredTechStackIds = listOf(200L)),
                99L to aiSnapshotOf(requiredTechStackIds = listOf(1L, 2L, 3L)),
            ),
        )
        given(jobApplicationEligibilityAccessor.findAllByJobIds(jobs.map { it.jobId }.toSet(), 1L)).willReturn(
            mapOf(1L to eligibilityOf()),
        )

        val result = service.generateForMember(1L, limit = 10)

        assertThat(result).isEmpty()
    }

    @Test
    fun `SIMILAR_JOBS 기준 Job의 AI 분석이 없으면 다른 Job으로 확장하지 않는다`() {
        val jobs = listOf(jobOf(1L))
        given(memberProfileQueryPort.findById(1L)).willReturn(memberOf())
        given(jobCandidateQueryPort.findAllPublished()).willReturn(jobs)
        given(memberJobPreferenceRepository.findExcludedJobIdsByMemberId(1L)).willReturn(emptyList())
        given(memberJobPreferenceRepository.findJobIdsByMemberIdAndExclusionType(1L, ExclusionType.SIMILAR_JOBS))
            .willReturn(listOf(99L))
        // 99(기준 Job)의 AI 분석이 COMPLETED가 아니라 결과 Map에 없다 -- 보수적 Fallback으로
        // 확장하지 않는다(Issue #167 §4).
        given(aiAnalysisSearchQueryPort.findCompletedByJobIds(listOf(1L, 99L))).willReturn(
            mapOf(1L to aiSnapshotOf(requiredTechStackIds = listOf(1L, 2L, 3L))),
        )
        given(jobApplicationEligibilityAccessor.findAllByJobIds(jobs.map { it.jobId }.toSet(), 1L)).willReturn(
            mapOf(1L to eligibilityOf()),
        )

        val result = service.generateForMember(1L, limit = 10)

        assertThat(result.map { it.jobId }).containsExactly(1L)
    }

    @Test
    fun `SIMILAR_JOBS 기준 Job이 있어도 AI Query는 한 번만 호출한다(Batch, N+1 방지)`() {
        val jobs = listOf(jobOf(1L), jobOf(2L))
        given(memberProfileQueryPort.findById(1L)).willReturn(memberOf())
        given(jobCandidateQueryPort.findAllPublished()).willReturn(jobs)
        given(memberJobPreferenceRepository.findExcludedJobIdsByMemberId(1L)).willReturn(emptyList())
        given(memberJobPreferenceRepository.findJobIdsByMemberIdAndExclusionType(1L, ExclusionType.SIMILAR_JOBS))
            .willReturn(listOf(99L))
        given(aiAnalysisSearchQueryPort.findCompletedByJobIds(anyCollection())).willReturn(
            mapOf(1L to aiSnapshotOf(), 2L to aiSnapshotOf(), 99L to aiSnapshotOf()),
        )
        given(jobApplicationEligibilityAccessor.findAllByJobIds(jobs.map { it.jobId }.toSet(), 1L)).willReturn(
            mapOf(1L to eligibilityOf(), 2L to eligibilityOf()),
        )

        service.generateForMember(1L, limit = 10)

        verify(aiAnalysisSearchQueryPort, times(1)).findCompletedByJobIds(anyCollection())
    }

    @Test
    fun `THIS_JOB 기준 Job은 SIMILAR_JOBS 확장 대상이 아니다(기존 NOT_INTERESTED 동작만 유지)`() {
        val jobs = listOf(jobOf(1L), jobOf(2L))
        given(memberProfileQueryPort.findById(1L)).willReturn(memberOf())
        given(jobCandidateQueryPort.findAllPublished()).willReturn(jobs)
        // job 1은 THIS_JOB으로 관심 없음 처리됐다 -- SIMILAR_JOBS 기준 Job 목록에는 포함되지 않는다.
        given(memberJobPreferenceRepository.findExcludedJobIdsByMemberId(1L)).willReturn(listOf(1L))
        given(memberJobPreferenceRepository.findJobIdsByMemberIdAndExclusionType(1L, ExclusionType.SIMILAR_JOBS))
            .willReturn(emptyList())
        given(aiAnalysisSearchQueryPort.findCompletedByJobIds(listOf(1L, 2L))).willReturn(
            mapOf(1L to aiSnapshotOf(), 2L to aiSnapshotOf()),
        )
        given(jobApplicationEligibilityAccessor.findAllByJobIds(jobs.map { it.jobId }.toSet(), 1L)).willReturn(
            mapOf(1L to eligibilityOf(), 2L to eligibilityOf()),
        )

        val result = service.generateForMember(1L, limit = 10)

        // job 1은 NOT_INTERESTED로 제외되지만, job 2는 job 1과 기술 스택이 같아도(techStackIds
        // 동일) THIS_JOB 확장 대상이 아니므로 제외되지 않는다.
        assertThat(result.map { it.jobId }).containsExactly(2L)
    }
}
