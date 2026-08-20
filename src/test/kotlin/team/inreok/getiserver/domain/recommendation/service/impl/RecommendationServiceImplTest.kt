package team.inreok.getiserver.domain.recommendation.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anySet
import org.mockito.BDDMockito.given
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import team.inreok.getiserver.domain.ai.query.AiAnalysisSearchQueryPort
import team.inreok.getiserver.domain.company.query.CompanyQuery
import team.inreok.getiserver.domain.company.query.CompanySummary
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.entity.type.PostingType
import team.inreok.getiserver.domain.job.query.JobBookmarkQuery
import team.inreok.getiserver.domain.job.query.JobBookmarkSort
import team.inreok.getiserver.domain.job.query.JobRecommendationCandidateQueryPort
import team.inreok.getiserver.domain.job.query.JobRecommendationCandidateSnapshot
import team.inreok.getiserver.domain.member.query.MemberApplicantSnapshot
import team.inreok.getiserver.domain.member.query.MemberApplicantSnapshotQueryPort
import team.inreok.getiserver.domain.recommendation.entity.MemberJobPreference
import team.inreok.getiserver.domain.recommendation.entity.Recommendation
import team.inreok.getiserver.domain.recommendation.entity.RecommendationGenerationState
import team.inreok.getiserver.domain.recommendation.entity.RecommendationPreference
import team.inreok.getiserver.domain.recommendation.entity.type.ExclusionType
import team.inreok.getiserver.domain.recommendation.entity.type.RecommendationGenerationStatus
import team.inreok.getiserver.domain.recommendation.entity.type.RecommendationReasonType
import team.inreok.getiserver.domain.recommendation.entity.type.SuitabilityLevel
import team.inreok.getiserver.domain.recommendation.exception.RecommendationExclusionAlreadyExistsException
import team.inreok.getiserver.domain.recommendation.exception.RecommendationExclusionNotFoundException
import team.inreok.getiserver.domain.recommendation.exception.RecommendationJobNotFoundException
import team.inreok.getiserver.domain.recommendation.exception.RecommendationNotEnrolledException
import team.inreok.getiserver.domain.recommendation.exception.RecommendationPreferenceWriteConflictException
import team.inreok.getiserver.domain.recommendation.repository.MemberJobPreferenceRepository
import team.inreok.getiserver.domain.recommendation.repository.RecommendationGenerationStateRepository
import team.inreok.getiserver.domain.recommendation.repository.RecommendationPreferenceRepository
import team.inreok.getiserver.domain.recommendation.repository.RecommendationRepository
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecommendationServiceImplTest {
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
        )
    }

    private val today: LocalDate = LocalDate.now()
    private val firstPage = PageRequest.of(0, 20)

    private fun jobOf(
        jobId: Long = 1L,
        companyId: Long = 100L,
    ) = JobRecommendationCandidateSnapshot(
        jobId = jobId,
        companyId = companyId,
        title = "백엔드 개발 인턴",
        status = "PUBLISHED",
        targetGrade = 3,
        publishedAt = LocalDateTime.of(2026, 8, 1, 0, 0),
        recruitmentEndedAt = LocalDateTime.of(2026, 12, 31, 0, 0),
        postingType = PostingType.GENERAL,
        applicationMethod = ApplicationMethod.INTERNAL,
        viewCount = 10,
    )

    private fun recommendationOf(
        jobId: Long = 1L,
        rank: Int = 1,
        reasonsJson: String? = """[{"type":"REQUIRED_SKILL_MATCH","matchedCount":3,"totalCount":4}]""",
        createdAt: LocalDateTime = LocalDateTime.of(2026, 8, 17, 6, 0),
    ) = Recommendation(
        memberId = 1L,
        jobId = jobId,
        recommendationDate = today,
        score = BigDecimal(82),
        suitability = SuitabilityLevel.RECOMMENDED,
        rank = rank,
        algorithmVersion = 1,
    ).apply {
        id = jobId
        reasons = reasonsJson
        this.createdAt = createdAt
    }

    private fun enrolledMember() =
        MemberApplicantSnapshot(
            memberId = 1L,
            name = "홍길동",
            email = "student@example.com",
            phone = null,
            academicStatus = "ENROLLED",
            grade = 3,
            cohort = 1,
            department = null,
            majors = emptyList(),
            techStacks = emptyList(),
            desiredJob = null,
        )

    // ---------- 조회 ----------

    @Test
    fun `설정 Row가 없으면(default) DISABLED와 빈 목록을 반환한다`() {
        given(recommendationPreferenceRepository.findByMemberId(1L)).willReturn(null)

        val result = service.getMyRecommendations(1L, null, firstPage)

        assertThat(result.enabled).isFalse()
        assertThat(result.status.name).isEqualTo("DISABLED")
        assertThat(result.content).isEmpty()
        assertThat(result.generatedAt).isNull()
        assertThat(result.nextGenerationAt).isNull()
    }

    @Test
    fun `enabled=false면 오늘자 결과가 있어도 DISABLED와 빈 목록을 반환한다`() {
        given(recommendationPreferenceRepository.findByMemberId(1L))
            .willReturn(RecommendationPreference(memberId = 1L, enabled = false))

        val result = service.getMyRecommendations(1L, null, firstPage)

        assertThat(result.status.name).isEqualTo("DISABLED")
        assertThat(result.content).isEmpty()
        verify(recommendationRepository, never()).findAllByMemberIdAndRecommendationDate(1L, today, null, firstPage)
    }

    @Test
    fun `enabled=true이고 오늘자 결과가 없으면 EMPTY를 반환한다`() {
        given(recommendationPreferenceRepository.findByMemberId(1L))
            .willReturn(RecommendationPreference(memberId = 1L, enabled = true))
        given(recommendationRepository.findAllByMemberIdAndRecommendationDate(1L, today, null, firstPage))
            .willReturn(PageImpl(emptyList(), firstPage, 0))
        given(recommendationRepository.findMaxCreatedAtByMemberIdAndRecommendationDate(1L, today)).willReturn(null)

        val result = service.getMyRecommendations(1L, null, firstPage)

        assertThat(result.enabled).isTrue()
        assertThat(result.status.name).isEqualTo("EMPTY")
        assertThat(result.content).isEmpty()
        assertThat(result.generatedAt).isNull()
    }

    @Test
    fun `enabled=true이고 결과가 있으면 READY와 함께 rank 순서대로 반환한다`() {
        val rows = listOf(recommendationOf(jobId = 1L, rank = 1), recommendationOf(jobId = 2L, rank = 2))
        given(recommendationPreferenceRepository.findByMemberId(1L))
            .willReturn(RecommendationPreference(memberId = 1L, enabled = true))
        given(recommendationRepository.findAllByMemberIdAndRecommendationDate(1L, today, null, firstPage))
            .willReturn(PageImpl(rows, firstPage, 2))
        given(recommendationRepository.findMaxCreatedAtByMemberIdAndRecommendationDate(1L, today))
            .willReturn(LocalDateTime.of(2026, 8, 17, 6, 0))
        given(jobRecommendationCandidateQueryPort.findAllByIds(setOf(1L, 2L)))
            .willReturn(mapOf(1L to jobOf(1L), 2L to jobOf(2L)))
        given(companyQuery.findActiveSummaries(setOf(100L), 1L))
            .willReturn(
                mapOf(
                    100L to CompanySummary(companyId = 100L, name = "인력개발원", logoUrl = "https://example.com/logo.png"),
                ),
            )
        given(memberJobPreferenceRepository.findBookmarkedJobIdsByMemberIdAndJobIdIn(1L, setOf(1L, 2L)))
            .willReturn(listOf(2L))

        val result = service.getMyRecommendations(1L, null, firstPage)

        assertThat(result.status.name).isEqualTo("READY")
        assertThat(result.content).hasSize(2)
        assertThat(result.content.map { it.job.jobId }).containsExactly(1L, 2L)
        assertThat(result.content.map { it.recommendationId }).containsExactly(1L, 2L)
        assertThat(
            result.content[0]
                .job.company
                ?.name,
        ).isEqualTo("인력개발원")
        assertThat(
            result.content[0]
                .job.company
                ?.logoUrl,
        ).isEqualTo("https://example.com/logo.png")
        assertThat(result.content[0].job.bookmarked).isFalse()
        assertThat(result.content[1].job.bookmarked).isTrue()
        assertThat(result.content[0].job.postingType).isEqualTo(PostingType.GENERAL)
        assertThat(result.content[0].job.status).isEqualTo(JobStatus.PUBLISHED)
        assertThat(result.content[0].score).isEqualTo(82)
        assertThat(result.content[0].suitabilityLevel).isEqualTo(SuitabilityLevel.RECOMMENDED)
        assertThat(result.content[0].reasons).hasSize(1)
        assertThat(result.content[0].reasons[0].type).isEqualTo(RecommendationReasonType.REQUIRED_SKILL_MATCH)
        assertThat(result.content[0].reasons[0].matchedCount).isEqualTo(3)
        assertThat(result.generatedAt).isEqualTo(LocalDateTime.of(2026, 8, 17, 6, 0))
        assertThat(result.totalElements).isEqualTo(2)
    }

    @Test
    fun `Generation State가 GENERATING이면 오늘자 결과를 다시 조회하지 않고 GENERATING과 빈 목록을 반환한다`() {
        given(recommendationPreferenceRepository.findByMemberId(1L))
            .willReturn(RecommendationPreference(memberId = 1L, enabled = true))
        given(recommendationGenerationStateRepository.findByMemberId(1L))
            .willReturn(
                RecommendationGenerationState(memberId = 1L, status = RecommendationGenerationStatus.GENERATING),
            )

        val result = service.getMyRecommendations(1L, null, firstPage)

        assertThat(result.enabled).isTrue()
        assertThat(result.status.name).isEqualTo("GENERATING")
        assertThat(result.content).isEmpty()
        assertThat(result.generatedAt).isNull()
        verify(recommendationRepository, never()).findAllByMemberIdAndRecommendationDate(1L, today, null, firstPage)
    }

    @Test
    fun `Generation State가 FAILED이면 FAILED와 빈 목록을 반환한다`() {
        given(recommendationPreferenceRepository.findByMemberId(1L))
            .willReturn(RecommendationPreference(memberId = 1L, enabled = true))
        given(recommendationGenerationStateRepository.findByMemberId(1L))
            .willReturn(RecommendationGenerationState(memberId = 1L, status = RecommendationGenerationStatus.FAILED))

        val result = service.getMyRecommendations(1L, null, firstPage)

        assertThat(result.status.name).isEqualTo("FAILED")
        assertThat(result.content).isEmpty()
        assertThat(result.generatedAt).isNull()
        verify(recommendationRepository, never()).findAllByMemberIdAndRecommendationDate(1L, today, null, firstPage)
    }

    @Test
    fun `Generation State가 READY이면 기존처럼 오늘자 결과를 조회한다`() {
        given(recommendationPreferenceRepository.findByMemberId(1L))
            .willReturn(RecommendationPreference(memberId = 1L, enabled = true))
        given(recommendationGenerationStateRepository.findByMemberId(1L))
            .willReturn(RecommendationGenerationState(memberId = 1L, status = RecommendationGenerationStatus.READY))
        given(recommendationRepository.findAllByMemberIdAndRecommendationDate(1L, today, null, firstPage))
            .willReturn(PageImpl(emptyList(), firstPage, 0))
        given(recommendationRepository.findMaxCreatedAtByMemberIdAndRecommendationDate(1L, today))
            .willReturn(LocalDateTime.of(2026, 8, 17, 6, 0))

        val result = service.getMyRecommendations(1L, null, firstPage)

        assertThat(result.status.name).isEqualTo("READY")
        assertThat(result.generatedAt).isEqualTo(LocalDateTime.of(2026, 8, 17, 6, 0))
    }

    @Test
    fun `nextGenerationAt은 Scheduler cron 기준 다음 실행 시각이다`() {
        given(recommendationPreferenceRepository.findByMemberId(1L))
            .willReturn(RecommendationPreference(memberId = 1L, enabled = true))
        given(recommendationRepository.findAllByMemberIdAndRecommendationDate(1L, today, null, firstPage))
            .willReturn(PageImpl(emptyList(), firstPage, 0))
        given(recommendationRepository.findMaxCreatedAtByMemberIdAndRecommendationDate(1L, today)).willReturn(null)

        val result = service.getMyRecommendations(1L, null, firstPage)

        assertThat(result.nextGenerationAt).isNotNull()
        assertThat(result.nextGenerationAt?.hour).isEqualTo(3)
        assertThat(result.nextGenerationAt?.isAfter(LocalDateTime.now())).isTrue()
    }

    @Test
    fun `cron이 비활성 Sentinel이면 nextGenerationAt은 null이다`() {
        val disabledCronService =
            RecommendationServiceImpl(
                recommendationRepository,
                recommendationPreferenceRepository,
                recommendationGenerationStateRepository,
                memberJobPreferenceRepository,
                jobRecommendationCandidateQueryPort,
                companyQuery,
                memberApplicantSnapshotQueryPort,
                JsonMapper(),
                "-",
                0.6,
                aiAnalysisSearchQueryPort,
            )
        given(recommendationPreferenceRepository.findByMemberId(1L))
            .willReturn(RecommendationPreference(memberId = 1L, enabled = true))
        given(recommendationRepository.findAllByMemberIdAndRecommendationDate(1L, today, null, firstPage))
            .willReturn(PageImpl(emptyList(), firstPage, 0))
        given(recommendationRepository.findMaxCreatedAtByMemberIdAndRecommendationDate(1L, today)).willReturn(null)

        val result = disabledCronService.getMyRecommendations(1L, null, firstPage)

        assertThat(result.nextGenerationAt).isNull()
    }

    @Test
    fun `suitabilityLevel Filter를 그대로 Repository에 전달한다`() {
        given(recommendationPreferenceRepository.findByMemberId(1L))
            .willReturn(RecommendationPreference(memberId = 1L, enabled = true))
        given(
            recommendationRepository.findAllByMemberIdAndRecommendationDate(
                1L,
                today,
                SuitabilityLevel.HIGHLY_RECOMMENDED,
                firstPage,
            ),
        ).willReturn(PageImpl(emptyList(), firstPage, 0))
        given(recommendationRepository.findMaxCreatedAtByMemberIdAndRecommendationDate(1L, today)).willReturn(null)

        service.getMyRecommendations(1L, SuitabilityLevel.HIGHLY_RECOMMENDED, firstPage)

        verify(recommendationRepository)
            .findAllByMemberIdAndRecommendationDate(1L, today, SuitabilityLevel.HIGHLY_RECOMMENDED, firstPage)
    }

    @Test
    fun `Job 표시 정보를 조회할 때 Job 개수와 무관하게 한 번만 Batch 호출한다`() {
        val rows = (1L..5L).map { recommendationOf(jobId = it, rank = it.toInt()) }
        given(recommendationPreferenceRepository.findByMemberId(1L))
            .willReturn(RecommendationPreference(memberId = 1L, enabled = true))
        given(recommendationRepository.findAllByMemberIdAndRecommendationDate(1L, today, null, firstPage))
            .willReturn(PageImpl(rows, firstPage, 5))
        given(recommendationRepository.findMaxCreatedAtByMemberIdAndRecommendationDate(1L, today))
            .willReturn(LocalDateTime.of(2026, 8, 17, 6, 0))
        given(jobRecommendationCandidateQueryPort.findAllByIds(setOf(1L, 2L, 3L, 4L, 5L)))
            .willReturn((1L..5L).associateWith { jobOf(it) })
        given(companyQuery.findActiveSummaries(setOf(100L), 1L)).willReturn(emptyMap())

        service.getMyRecommendations(1L, null, firstPage)

        verify(jobRecommendationCandidateQueryPort, times(1)).findAllByIds(anySet())
        // findActiveSummaries(companyIds, requesterId)는 Kotlin Default Parameter라
        // ArgumentMatcher(anySet())와 섞으면 Mockito가 Default Parameter 분기(-$default) 호출을
        // Matcher 개수 불일치로 오인한다(InvalidUseOfMatchersException). 정확한 값으로 검증한다.
        verify(companyQuery, times(1)).findActiveSummaries(setOf(100L), 1L)
    }

    @Test
    fun `추천 생성 이후 삭제된 Job은 목록에서 빠진다`() {
        val rows = listOf(recommendationOf(jobId = 1L, rank = 1), recommendationOf(jobId = 2L, rank = 2))
        given(recommendationPreferenceRepository.findByMemberId(1L))
            .willReturn(RecommendationPreference(memberId = 1L, enabled = true))
        given(recommendationRepository.findAllByMemberIdAndRecommendationDate(1L, today, null, firstPage))
            .willReturn(PageImpl(rows, firstPage, 2))
        given(recommendationRepository.findMaxCreatedAtByMemberIdAndRecommendationDate(1L, today))
            .willReturn(LocalDateTime.of(2026, 8, 17, 6, 0))
        // jobId=2는 삭제되어 결과 Map에서 빠진 것으로 Stub한다.
        given(jobRecommendationCandidateQueryPort.findAllByIds(setOf(1L, 2L))).willReturn(mapOf(1L to jobOf(1L)))
        given(companyQuery.findActiveSummaries(setOf(100L), 1L)).willReturn(emptyMap())

        val result = service.getMyRecommendations(1L, null, firstPage)

        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].job.jobId).isEqualTo(1L)
    }

    @Test
    fun `Reason 파싱에 실패해도 전체 조회는 실패하지 않고 그 항목의 이유만 빈 목록이 된다`() {
        val rows = listOf(recommendationOf(jobId = 1L, rank = 1, reasonsJson = "not-a-valid-json"))
        given(recommendationPreferenceRepository.findByMemberId(1L))
            .willReturn(RecommendationPreference(memberId = 1L, enabled = true))
        given(recommendationRepository.findAllByMemberIdAndRecommendationDate(1L, today, null, firstPage))
            .willReturn(PageImpl(rows, firstPage, 1))
        given(recommendationRepository.findMaxCreatedAtByMemberIdAndRecommendationDate(1L, today))
            .willReturn(LocalDateTime.of(2026, 8, 17, 6, 0))
        given(jobRecommendationCandidateQueryPort.findAllByIds(setOf(1L))).willReturn(mapOf(1L to jobOf(1L)))
        given(companyQuery.findActiveSummaries(setOf(100L), 1L)).willReturn(emptyMap())

        val result = service.getMyRecommendations(1L, null, firstPage)

        assertThat(result.status.name).isEqualTo("READY")
        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].reasons).isEmpty()
    }

    // ---------- ON/OFF ----------

    @Test
    fun `재학 중인 회원은 설정 Row가 없으면 Upsert로 새로 만든다`() {
        given(memberApplicantSnapshotQueryPort.findById(1L)).willReturn(enrolledMember())
        given(recommendationPreferenceRepository.findByMemberId(1L))
            .willReturn(
                RecommendationPreference(memberId = 1L, enabled = true).apply {
                    updatedAt =
                        LocalDateTime.of(2026, 8, 17, 6, 0)
                },
            )

        val result = service.updateSetting(1L, true)

        verify(recommendationPreferenceRepository).upsert(1L, true)
        assertThat(result.enabled).isTrue()
        assertThat(result.updatedAt).isEqualTo(LocalDateTime.of(2026, 8, 17, 6, 0))
    }

    @Test
    fun `설정 Row가 있으면 Upsert로 값을 갱신한다`() {
        given(memberApplicantSnapshotQueryPort.findById(1L)).willReturn(enrolledMember())
        given(recommendationPreferenceRepository.findByMemberId(1L))
            .willReturn(
                RecommendationPreference(memberId = 1L, enabled = false).apply {
                    updatedAt =
                        LocalDateTime.of(2026, 8, 17, 6, 0)
                },
            )

        val result = service.updateSetting(1L, false)

        verify(recommendationPreferenceRepository).upsert(1L, false)
        assertThat(result.enabled).isFalse()
    }

    @Test
    fun `졸업생은 추천 설정을 변경할 수 없다`() {
        given(memberApplicantSnapshotQueryPort.findById(1L))
            .willReturn(enrolledMember().copy(academicStatus = "GRADUATED"))

        assertThatThrownBy { service.updateSetting(1L, true) }
            .isInstanceOf(RecommendationNotEnrolledException::class.java)
        verify(recommendationPreferenceRepository, never()).upsert(anyLong(), anyBoolean())
    }

    @Test
    fun `자퇴생도 추천 설정을 변경할 수 없다`() {
        given(memberApplicantSnapshotQueryPort.findById(1L))
            .willReturn(enrolledMember().copy(academicStatus = "WITHDRAWN"))

        assertThatThrownBy { service.updateSetting(1L, true) }
            .isInstanceOf(RecommendationNotEnrolledException::class.java)
    }

    @Test
    fun `회원 Profile을 찾을 수 없으면 설정을 변경할 수 없다`() {
        given(memberApplicantSnapshotQueryPort.findById(1L)).willReturn(null)

        assertThatThrownBy { service.updateSetting(1L, true) }
            .isInstanceOf(RecommendationNotEnrolledException::class.java)
    }

    @Test
    fun `Upsert 직후 조회되지 않으면 예외를 던진다(불변식 위반 방어)`() {
        given(memberApplicantSnapshotQueryPort.findById(1L)).willReturn(enrolledMember())
        given(recommendationPreferenceRepository.findByMemberId(1L)).willReturn(null)

        assertThatThrownBy { service.updateSetting(1L, true) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    // ---------- 관심 없음 등록 ----------

    @Test
    fun `관심 없음을 등록하면 exclusion과 exclusionCreatedAt을 저장하고 오늘자 Recommendation을 지운다`() {
        given(jobRecommendationCandidateQueryPort.findAllByIds(setOf(1L))).willReturn(mapOf(1L to jobOf(1L)))
        given(memberJobPreferenceRepository.existsByMemberIdAndJobIdAndExclusionIsNotNull(1L, 1L)).willReturn(false)
        given(memberJobPreferenceRepository.findByMemberIdAndJobId(1L, 1L)).willReturn(null)
        given(memberJobPreferenceRepository.saveAndFlush(preferenceCaptor.capture())).willAnswer { invocation ->
            (invocation.arguments[0] as MemberJobPreference).apply { id = 10L }
        }
        given(companyQuery.findActiveSummary(100L, 1L))
            .willReturn(CompanySummary(companyId = 100L, name = "인력개발원"))

        val result = service.registerExclusion(1L, 1L, ExclusionType.THIS_JOB)

        assertThat(preferenceCaptor.value.exclusion).isEqualTo(ExclusionType.THIS_JOB)
        assertThat(preferenceCaptor.value.exclusionCreatedAt).isNotNull()
        assertThat(result.exclusionId).isEqualTo(10L)
        assertThat(result.exclusionType).isEqualTo(ExclusionType.THIS_JOB)
        assertThat(result.job.jobId).isEqualTo(1L)
        verify(recommendationRepository).deleteAllByMemberIdAndJobId(1L, 1L)
    }

    @Test
    fun `SIMILAR_JOBS도 그대로 저장한다`() {
        given(jobRecommendationCandidateQueryPort.findAllByIds(setOf(1L))).willReturn(mapOf(1L to jobOf(1L)))
        given(memberJobPreferenceRepository.existsByMemberIdAndJobIdAndExclusionIsNotNull(1L, 1L)).willReturn(false)
        given(memberJobPreferenceRepository.findByMemberIdAndJobId(1L, 1L)).willReturn(null)
        given(memberJobPreferenceRepository.saveAndFlush(preferenceCaptor.capture())).willAnswer { invocation ->
            (invocation.arguments[0] as MemberJobPreference).apply { id = 11L }
        }

        val result = service.registerExclusion(1L, 1L, ExclusionType.SIMILAR_JOBS)

        assertThat(preferenceCaptor.value.exclusion).isEqualTo(ExclusionType.SIMILAR_JOBS)
        assertThat(result.exclusionType).isEqualTo(ExclusionType.SIMILAR_JOBS)
    }

    @Test
    fun `이미 관심 없음인 공고를 다시 등록하면 409에 해당하는 예외를 던진다`() {
        given(jobRecommendationCandidateQueryPort.findAllByIds(setOf(1L))).willReturn(mapOf(1L to jobOf(1L)))
        given(memberJobPreferenceRepository.existsByMemberIdAndJobIdAndExclusionIsNotNull(1L, 1L)).willReturn(true)

        assertThatThrownBy { service.registerExclusion(1L, 1L, ExclusionType.THIS_JOB) }
            .isInstanceOf(RecommendationExclusionAlreadyExistsException::class.java)
        verify(memberJobPreferenceRepository, never()).saveAndFlush(anyPreference())
        verify(recommendationRepository, never()).deleteAllByMemberIdAndJobId(1L, 1L)
    }

    @Test
    fun `사전 확인을 통과해도 동시 등록으로 DB 제약을 위반하면 409에 해당하는 예외로 변환한다`() {
        // 코드리뷰 반영: existsByMemberIdAndJobIdAndExclusionIsNotNull과 saveAndFlush 사이의
        // TOCTOU를 재현한다 -- 사전 확인은 false(관심 없음 없음)를 통과했지만, 그 사이 동시
        // 요청이 먼저 Row를 만들어 uk_member_job_preferences_member_job UNIQUE 제약을 위반한
        // 상황을 Mock으로 흉내낸다.
        //
        // 경합 상대가 관심 없음 등록인지 북마크 등록인지 이 시점에는 알 수 없어(PR #178 코드리뷰
        // 반영) EXCLUSION_ALREADY_EXISTS로 단정하지 않고 공통 PREFERENCE_WRITE_CONFLICT(재시도
        // 가능)로 변환한다 -- saveNewPreferenceRow KDoc 참고.
        given(jobRecommendationCandidateQueryPort.findAllByIds(setOf(1L))).willReturn(mapOf(1L to jobOf(1L)))
        given(memberJobPreferenceRepository.existsByMemberIdAndJobIdAndExclusionIsNotNull(1L, 1L)).willReturn(false)
        given(memberJobPreferenceRepository.findByMemberIdAndJobId(1L, 1L)).willReturn(null)
        given(memberJobPreferenceRepository.saveAndFlush(anyPreference()))
            .willThrow(DataIntegrityViolationException("uk_member_job_preferences_member_job"))

        assertThatThrownBy { service.registerExclusion(1L, 1L, ExclusionType.THIS_JOB) }
            .isInstanceOf(RecommendationPreferenceWriteConflictException::class.java)
        verify(recommendationRepository, never()).deleteAllByMemberIdAndJobId(1L, 1L)
    }

    @Test
    fun `이미 북마크된 Job을 관심 없음으로 등록해도 bookmarked는 유지된다`() {
        val existing = MemberJobPreference(memberId = 1L, jobId = 1L, bookmarked = true).apply { id = 20L }
        given(jobRecommendationCandidateQueryPort.findAllByIds(setOf(1L))).willReturn(mapOf(1L to jobOf(1L)))
        given(memberJobPreferenceRepository.existsByMemberIdAndJobIdAndExclusionIsNotNull(1L, 1L)).willReturn(false)
        given(memberJobPreferenceRepository.findByMemberIdAndJobId(1L, 1L)).willReturn(existing)
        given(memberJobPreferenceRepository.saveAndFlush(existing)).willReturn(existing)

        val result = service.registerExclusion(1L, 1L, ExclusionType.THIS_JOB)

        assertThat(existing.bookmarked).isTrue()
        assertThat(existing.exclusion).isEqualTo(ExclusionType.THIS_JOB)
        assertThat(result.job.bookmarked).isTrue()
    }

    @Test
    fun `존재하지 않는 공고를 관심 없음으로 등록하면 RecommendationJobNotFoundException을 던진다`() {
        given(jobRecommendationCandidateQueryPort.findAllByIds(setOf(999L))).willReturn(emptyMap())

        assertThatThrownBy { service.registerExclusion(1L, 999L, ExclusionType.THIS_JOB) }
            .isInstanceOf(RecommendationJobNotFoundException::class.java)
        verify(memberJobPreferenceRepository, never()).save(anyPreference())
        verify(recommendationRepository, never()).deleteAllByMemberIdAndJobId(1L, 999L)
    }

    // ---------- 관심 없음 목록 ----------

    @Test
    fun `북마크 목록은 Job 필터를 전달하고 결과를 실제 북마크 상태로 반환한다`() {
        val query = JobBookmarkQuery("백엔드", PostingType.GENERAL, "GENERAL", JobBookmarkSort.DEADLINE)
        given(memberJobPreferenceRepository.findBookmarkedJobIdsByMemberIdAndQuery(1L, query, firstPage))
            .willReturn(PageImpl(listOf(1L), firstPage, 1))
        given(jobRecommendationCandidateQueryPort.findAllByIds(setOf(1L))).willReturn(mapOf(1L to jobOf()))
        given(companyQuery.findActiveSummaries(setOf(100L), 1L)).willReturn(emptyMap())

        val result = service.listBookmarkedJobs(1L, query, firstPage)

        assertThat(result.content).hasSize(1)
        assertThat(result.content.single().jobId).isEqualTo(1L)
        assertThat(result.content.single().bookmarked).isTrue()
        assertThat(result.totalElements).isEqualTo(1)
        verify(memberJobPreferenceRepository).findBookmarkedJobIdsByMemberIdAndQuery(1L, query, firstPage)
        verify(jobRecommendationCandidateQueryPort).findAllByIds(setOf(1L))
        verify(companyQuery).findActiveSummaries(setOf(100L), 1L)
    }

    @Test
    fun `관심 없음 목록을 조회하면 본인 것만 Job 정보와 함께 반환한다`() {
        val preference =
            MemberJobPreference(memberId = 1L, jobId = 1L).apply {
                id = 30L
                exclusion = ExclusionType.THIS_JOB
                exclusionCreatedAt = LocalDateTime.of(2026, 8, 17, 6, 0)
            }
        given(memberJobPreferenceRepository.findAllExclusionsByMemberId(1L, null, firstPage))
            .willReturn(PageImpl(listOf(preference), firstPage, 1))
        given(jobRecommendationCandidateQueryPort.findAllByIds(setOf(1L))).willReturn(mapOf(1L to jobOf(1L)))
        given(companyQuery.findActiveSummaries(setOf(100L), 1L)).willReturn(emptyMap())

        val result = service.listExclusions(1L, null, firstPage)

        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].exclusionId).isEqualTo(30L)
        assertThat(result.content[0].exclusionType).isEqualTo(ExclusionType.THIS_JOB)
        assertThat(result.totalElements).isEqualTo(1)
    }

    @Test
    fun `exclusionType Filter를 그대로 Repository에 전달한다`() {
        given(memberJobPreferenceRepository.findAllExclusionsByMemberId(1L, ExclusionType.SIMILAR_JOBS, firstPage))
            .willReturn(PageImpl(emptyList(), firstPage, 0))

        service.listExclusions(1L, ExclusionType.SIMILAR_JOBS, firstPage)

        verify(memberJobPreferenceRepository).findAllExclusionsByMemberId(1L, ExclusionType.SIMILAR_JOBS, firstPage)
    }

    @Test
    fun `관심 없음 목록 조회 시 추천 생성 이후 삭제된 Job은 목록에서 빠진다`() {
        val preference =
            MemberJobPreference(memberId = 1L, jobId = 999L).apply {
                id = 31L
                exclusion = ExclusionType.THIS_JOB
                exclusionCreatedAt = LocalDateTime.now()
            }
        given(memberJobPreferenceRepository.findAllExclusionsByMemberId(1L, null, firstPage))
            .willReturn(PageImpl(listOf(preference), firstPage, 1))
        given(jobRecommendationCandidateQueryPort.findAllByIds(setOf(999L))).willReturn(emptyMap())
        given(companyQuery.findActiveSummaries(emptySet(), 1L)).willReturn(emptyMap())

        val result = service.listExclusions(1L, null, firstPage)

        assertThat(result.content).isEmpty()
    }

    // ---------- 관심 없음 해제 ----------

    @Test
    fun `관심 없음을 해제하면 exclusion과 exclusionCreatedAt을 지우고 Row도 함께 지운다(bookmarked 없음)`() {
        val existing =
            MemberJobPreference(memberId = 1L, jobId = 1L).apply {
                id = 40L
                exclusion = ExclusionType.THIS_JOB
                exclusionCreatedAt = LocalDateTime.now()
            }
        given(memberJobPreferenceRepository.findByIdAndMemberId(40L, 1L)).willReturn(existing)

        service.removeExclusion(1L, 40L)

        verify(memberJobPreferenceRepository).delete(existing)
        verify(memberJobPreferenceRepository, never()).save(anyPreference())
        assertThat(existing.exclusion).isNull()
        assertThat(existing.exclusionCreatedAt).isNull()
    }

    @Test
    fun `북마크가 있는 Job은 관심 없음만 해제하고 Row는 유지한다`() {
        val existing =
            MemberJobPreference(memberId = 1L, jobId = 1L, bookmarked = true).apply {
                id = 41L
                exclusion = ExclusionType.THIS_JOB
            }
        given(memberJobPreferenceRepository.findByIdAndMemberId(41L, 1L)).willReturn(existing)

        service.removeExclusion(1L, 41L)

        verify(memberJobPreferenceRepository).save(existing)
        verify(memberJobPreferenceRepository, never()).delete(anyPreference())
        assertThat(existing.exclusion).isNull()
    }

    @Test
    fun `해제 대상이 없으면 RecommendationExclusionNotFoundException을 던진다`() {
        given(memberJobPreferenceRepository.findByIdAndMemberId(999L, 1L)).willReturn(null)

        assertThatThrownBy { service.removeExclusion(1L, 999L) }
            .isInstanceOf(RecommendationExclusionNotFoundException::class.java)
        verify(memberJobPreferenceRepository, never()).save(anyPreference())
        verify(memberJobPreferenceRepository, never()).delete(anyPreference())
    }

    @Test
    fun `다른 회원의 exclusionId는 찾을 수 없다(소유권 검증)`() {
        // findByIdAndMemberId 자체가 memberId까지 조건에 포함하므로, 다른 회원의 exclusionId를
        // 요청하면 Repository가 null을 돌려준다 -- Service는 존재하지 않는 경우와 구분하지 않는다.
        given(memberJobPreferenceRepository.findByIdAndMemberId(40L, 2L)).willReturn(null)

        assertThatThrownBy { service.removeExclusion(2L, 40L) }
            .isInstanceOf(RecommendationExclusionNotFoundException::class.java)
    }

    @Test
    fun `해제는 새로운 Recommendation을 생성하거나 조회하지 않는다`() {
        given(memberJobPreferenceRepository.findByIdAndMemberId(40L, 1L)).willReturn(null)

        assertThatThrownBy {
            service.removeExclusion(1L, 40L)
        }.isInstanceOf(RecommendationExclusionNotFoundException::class.java)

        verifyNoInteractions(recommendationRepository, jobRecommendationCandidateQueryPort, companyQuery)
    }

    private fun anyPreference(): MemberJobPreference =
        any(MemberJobPreference::class.java) ?: MemberJobPreference(memberId = 0L, jobId = 0L)
}
