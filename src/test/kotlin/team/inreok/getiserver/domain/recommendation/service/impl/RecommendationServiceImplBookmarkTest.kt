package team.inreok.getiserver.domain.recommendation.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.springframework.dao.DataIntegrityViolationException
import team.inreok.getiserver.domain.ai.query.AiAnalysisSearchQueryPort
import team.inreok.getiserver.domain.company.query.CompanyQuery
import team.inreok.getiserver.domain.company.query.CompanySummary
import team.inreok.getiserver.domain.job.access.JobAiAnalysisAccessor
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.PostingType
import team.inreok.getiserver.domain.job.query.JobRecommendationCandidateQueryPort
import team.inreok.getiserver.domain.job.query.JobRecommendationCandidateSnapshot
import team.inreok.getiserver.domain.member.query.MemberApplicantSnapshotQueryPort
import team.inreok.getiserver.domain.recommendation.entity.MemberJobPreference
import team.inreok.getiserver.domain.recommendation.entity.type.ExclusionType
import team.inreok.getiserver.domain.recommendation.exception.RecommendationBookmarkAlreadyExistsException
import team.inreok.getiserver.domain.recommendation.exception.RecommendationBookmarkNotFoundException
import team.inreok.getiserver.domain.recommendation.exception.RecommendationJobNotFoundException
import team.inreok.getiserver.domain.recommendation.exception.RecommendationPreferenceWriteConflictException
import team.inreok.getiserver.domain.recommendation.repository.MemberJobPreferenceRepository
import team.inreok.getiserver.domain.recommendation.repository.RecommendationGenerationStateRepository
import team.inreok.getiserver.domain.recommendation.repository.RecommendationPreferenceRepository
import team.inreok.getiserver.domain.recommendation.repository.RecommendationRepository
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime

/**
 * [RecommendationServiceImpl.registerBookmark]/[RecommendationServiceImpl.removeBookmark]을
 * 검증한다(Issue #170). `RecommendationServiceImplTest`와 나눠 detekt `LargeClass`를 넘지 않게
 * 한다 -- `RecommendationServiceImplSimilarJobsTest`와 같은 분리 관례를 따른다.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecommendationServiceImplBookmarkTest {
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

    private fun stubJobExists(jobId: Long = 1L) {
        given(jobRecommendationCandidateQueryPort.findAllByIds(setOf(jobId))).willReturn(mapOf(jobId to jobOf(jobId)))
    }

    private fun stubSave() {
        given(memberJobPreferenceRepository.saveAndFlush(preferenceCaptor.capture())).willAnswer { invocation ->
            (invocation.arguments[0] as MemberJobPreference).apply { id = 1L }
        }
    }

    // Kotlin non-null 파라미터에 bare any()를 쓰면 null 반환으로 NPE가 나므로 Elvis로 기본값을
    // 채운다(RecommendationServiceImplTest.anyPreference()와 동일한 관례).
    private fun anyPreference(): MemberJobPreference =
        any(MemberJobPreference::class.java) ?: MemberJobPreference(memberId = 0L, jobId = 0L)

    // ---------- 등록 ----------

    @Test
    fun `Preference Row가 없으면 새로 만들어 북마크를 등록한다`() {
        stubJobExists()
        given(memberJobPreferenceRepository.findByMemberIdAndJobId(1L, 1L)).willReturn(null)
        stubSave()
        given(companyQuery.findActiveSummary(100L, 1L))
            .willReturn(CompanySummary(companyId = 100L, name = "인력개발원"))

        val response = service.registerBookmark(1L, 1L)

        assertThat(preferenceCaptor.value.memberId).isEqualTo(1L)
        assertThat(preferenceCaptor.value.jobId).isEqualTo(1L)
        assertThat(preferenceCaptor.value.bookmarked).isTrue()
        assertThat(response.bookmarked).isTrue()
        assertThat(response.jobId).isEqualTo(1L)
    }

    @Test
    fun `Preference Row가 있고 bookmarked=false면 true로 갱신한다`() {
        stubJobExists()
        val existing = MemberJobPreference(memberId = 1L, jobId = 1L, bookmarked = false)
        given(memberJobPreferenceRepository.findByMemberIdAndJobId(1L, 1L)).willReturn(existing)
        given(memberJobPreferenceRepository.saveAndFlush(existing)).willReturn(existing)

        service.registerBookmark(1L, 1L)

        assertThat(existing.bookmarked).isTrue()
    }

    @Test
    fun `exclusion=THIS_JOB인 Row를 북마크 등록하면 bookmarked만 true가 되고 exclusion은 유지된다`() {
        stubJobExists()
        val existing =
            MemberJobPreference(memberId = 1L, jobId = 1L, bookmarked = false).apply {
                exclusion = ExclusionType.THIS_JOB
            }
        given(memberJobPreferenceRepository.findByMemberIdAndJobId(1L, 1L)).willReturn(existing)
        given(memberJobPreferenceRepository.saveAndFlush(existing)).willReturn(existing)

        service.registerBookmark(1L, 1L)

        assertThat(existing.bookmarked).isTrue()
        assertThat(existing.exclusion).isEqualTo(ExclusionType.THIS_JOB)
    }

    @Test
    fun `exclusion=SIMILAR_JOBS인 Row를 북마크 등록해도 exclusion은 유지된다`() {
        stubJobExists()
        val existing =
            MemberJobPreference(memberId = 1L, jobId = 1L, bookmarked = false).apply {
                exclusion = ExclusionType.SIMILAR_JOBS
            }
        given(memberJobPreferenceRepository.findByMemberIdAndJobId(1L, 1L)).willReturn(existing)
        given(memberJobPreferenceRepository.saveAndFlush(existing)).willReturn(existing)

        service.registerBookmark(1L, 1L)

        assertThat(existing.bookmarked).isTrue()
        assertThat(existing.exclusion).isEqualTo(ExclusionType.SIMILAR_JOBS)
    }

    @Test
    fun `이미 북마크한 공고를 다시 등록하면 409에 해당하는 예외를 던진다`() {
        stubJobExists()
        val existing = MemberJobPreference(memberId = 1L, jobId = 1L, bookmarked = true)
        given(memberJobPreferenceRepository.findByMemberIdAndJobId(1L, 1L)).willReturn(existing)

        assertThatThrownBy { service.registerBookmark(1L, 1L) }
            .isInstanceOf(RecommendationBookmarkAlreadyExistsException::class.java)
        verify(memberJobPreferenceRepository, never()).saveAndFlush(existing)
    }

    @Test
    fun `사전 확인을 통과해도 동시 등록으로 DB 제약을 위반하면 재시도 가능한 예외로 변환한다`() {
        // 코드리뷰 반영(PR #178): findByMemberIdAndJobId 사전 확인과 saveAndFlush 사이의
        // TOCTOU를 재현한다 -- 사전 확인은 null(북마크 없음)을 통과했지만, 그 사이 동시 요청(관심
        // 없음 등록일 수도 있다)이 먼저 Row를 만들어 uk_member_job_preferences_member_job UNIQUE
        // 제약을 위반한 상황을 Mock으로 흉내낸다. 경합 상대가 관심 없음 등록이었을 수도 있어
        // BOOKMARK_ALREADY_EXISTS로 단정하지 않고 공통 PREFERENCE_WRITE_CONFLICT로 변환해야 한다
        // (실제로는 북마크되지 않았는데 "이미 북마크됨"으로 잘못 응답하는 것을 방지).
        stubJobExists()
        given(memberJobPreferenceRepository.findByMemberIdAndJobId(1L, 1L)).willReturn(null)
        given(memberJobPreferenceRepository.saveAndFlush(anyPreference()))
            .willThrow(DataIntegrityViolationException("uk_member_job_preferences_member_job"))

        assertThatThrownBy { service.registerBookmark(1L, 1L) }
            .isInstanceOf(RecommendationPreferenceWriteConflictException::class.java)
        verify(recommendationRepository, never()).deleteAllByMemberIdAndJobId(1L, 1L)
    }

    @Test
    fun `없는 공고를 북마크하면 RecommendationJobNotFoundException을 던진다`() {
        given(jobRecommendationCandidateQueryPort.findAllByIds(setOf(999L))).willReturn(emptyMap())

        assertThatThrownBy { service.registerBookmark(1L, 999L) }
            .isInstanceOf(RecommendationJobNotFoundException::class.java)
        verify(memberJobPreferenceRepository, never()).findByMemberIdAndJobId(1L, 999L)
    }

    @Test
    fun `북마크 등록 시 오늘자 해당 Job Recommendation을 즉시 삭제한다`() {
        stubJobExists()
        given(memberJobPreferenceRepository.findByMemberIdAndJobId(1L, 1L)).willReturn(null)
        stubSave()

        service.registerBookmark(1L, 1L)

        verify(recommendationRepository).deleteAllByMemberIdAndJobId(1L, 1L)
    }

    // ---------- 해제 ----------

    @Test
    fun `bookmarked=true, exclusion=null이면 해제하고 Row도 지운다`() {
        val existing = MemberJobPreference(memberId = 1L, jobId = 1L, bookmarked = true)
        given(memberJobPreferenceRepository.findByMemberIdAndJobId(1L, 1L)).willReturn(existing)

        service.removeBookmark(1L, 1L)

        assertThat(existing.bookmarked).isFalse()
        verify(memberJobPreferenceRepository).delete(existing)
        verify(memberJobPreferenceRepository, never()).save(existing)
    }

    @Test
    fun `exclusion=THIS_JOB이 남아 있으면 해제해도 Row를 지우지 않는다`() {
        val existing =
            MemberJobPreference(memberId = 1L, jobId = 1L, bookmarked = true).apply {
                exclusion = ExclusionType.THIS_JOB
            }
        given(memberJobPreferenceRepository.findByMemberIdAndJobId(1L, 1L)).willReturn(existing)

        service.removeBookmark(1L, 1L)

        assertThat(existing.bookmarked).isFalse()
        assertThat(existing.exclusion).isEqualTo(ExclusionType.THIS_JOB)
        verify(memberJobPreferenceRepository).save(existing)
        verify(memberJobPreferenceRepository, never()).delete(existing)
    }

    @Test
    fun `exclusion=SIMILAR_JOBS가 남아 있으면 해제해도 Row를 지우지 않는다`() {
        val existing =
            MemberJobPreference(memberId = 1L, jobId = 1L, bookmarked = true).apply {
                exclusion = ExclusionType.SIMILAR_JOBS
            }
        given(memberJobPreferenceRepository.findByMemberIdAndJobId(1L, 1L)).willReturn(existing)

        service.removeBookmark(1L, 1L)

        assertThat(existing.bookmarked).isFalse()
        verify(memberJobPreferenceRepository).save(existing)
        verify(memberJobPreferenceRepository, never()).delete(existing)
    }

    @Test
    fun `Row가 없으면 해제 시 RecommendationBookmarkNotFoundException을 던진다`() {
        given(memberJobPreferenceRepository.findByMemberIdAndJobId(1L, 999L)).willReturn(null)

        assertThatThrownBy { service.removeBookmark(1L, 999L) }
            .isInstanceOf(RecommendationBookmarkNotFoundException::class.java)
    }

    @Test
    fun `이미 bookmarked=false면 해제 시 RecommendationBookmarkNotFoundException을 던진다`() {
        val existing = MemberJobPreference(memberId = 1L, jobId = 1L, bookmarked = false)
        given(memberJobPreferenceRepository.findByMemberIdAndJobId(1L, 1L)).willReturn(existing)

        assertThatThrownBy { service.removeBookmark(1L, 1L) }
            .isInstanceOf(RecommendationBookmarkNotFoundException::class.java)
        verify(memberJobPreferenceRepository, never()).delete(existing)
        verify(memberJobPreferenceRepository, never()).save(existing)
    }

    @Test
    fun `북마크 해제는 새로운 Recommendation을 생성하거나 조회하지 않는다`() {
        val existing = MemberJobPreference(memberId = 1L, jobId = 1L, bookmarked = true)
        given(memberJobPreferenceRepository.findByMemberIdAndJobId(1L, 1L)).willReturn(existing)

        service.removeBookmark(1L, 1L)

        verifyNoInteractions(recommendationRepository)
    }
}
