package team.inreok.getiserver.persistence

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import team.inreok.getiserver.domain.company.entity.Company
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.company.repository.CompanyRepository
import team.inreok.getiserver.domain.job.entity.Job
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.entity.type.PostingType
import team.inreok.getiserver.domain.job.repository.JobRepository
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.recommendation.entity.MemberJobPreference
import team.inreok.getiserver.domain.recommendation.entity.Recommendation
import team.inreok.getiserver.domain.recommendation.entity.RecommendationGenerationState
import team.inreok.getiserver.domain.recommendation.entity.RecommendationPreference
import team.inreok.getiserver.domain.recommendation.entity.type.ExclusionType
import team.inreok.getiserver.domain.recommendation.entity.type.RecommendationGenerationStatus
import team.inreok.getiserver.domain.recommendation.entity.type.SuitabilityLevel
import team.inreok.getiserver.domain.recommendation.repository.MemberJobPreferenceRepository
import team.inreok.getiserver.domain.recommendation.repository.RecommendationGenerationStateRepository
import team.inreok.getiserver.domain.recommendation.repository.RecommendationPreferenceRepository
import team.inreok.getiserver.domain.recommendation.repository.RecommendationRepository
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Recommendation Persistence를 실제 PostgreSQL로 검증한다. Service Test(Mock Repository)로는
 * Migration이 추가한 Column/Table, Unique 제약, JSONB `reasons` 저장/조회를 확인할 수 없다.
 *
 * R2(Issue #148, V22 Migration)의 `recommendations`/`member_job_preferences` 검증에 R3(Issue
 * #152, V23 Migration)의 `recommendation_preferences`, Notion 계약 정합성(Issue #155, V24
 * Migration)의 `member_job_preferences` Surrogate PK/`exclusion_created_at` 검증, R4(Issue #160,
 * V25 Migration)의 `recommendation_generation_states` Upsert 검증을 이어서 추가했다 -- 네 Phase
 * 모두 같은 Member/Job/Company Fixture를 공유해 별도 Testcontainers Container를 새로 띄우지
 * 않는다.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration::class)
class RecommendationPersistenceIntegrationTest
    @Autowired
    constructor(
        private val recommendationRepository: RecommendationRepository,
        private val recommendationPreferenceRepository: RecommendationPreferenceRepository,
        private val recommendationGenerationStateRepository: RecommendationGenerationStateRepository,
        private val memberJobPreferenceRepository: MemberJobPreferenceRepository,
        private val memberRepository: MemberRepository,
        private val jobRepository: JobRepository,
        private val companyRepository: CompanyRepository,
    ) {
        private var memberId: Long = 0
        private var jobId: Long = 0
        private val today: LocalDate = LocalDate.now()
        private val firstPage = PageRequest.of(0, 20)

        @BeforeEach
        fun setUp() {
            recommendationRepository.deleteAll()
            recommendationPreferenceRepository.deleteAll()
            recommendationGenerationStateRepository.deleteAll()
            memberJobPreferenceRepository.deleteAll()
            jobRepository.deleteAll()
            memberRepository.deleteAll()
            companyRepository.deleteAll()

            val company = companyRepository.saveAndFlush(Company(name = "GETI", type = CompanyType.GENERAL))
            val job =
                jobRepository.saveAndFlush(
                    Job(
                        companyId = requireNotNull(company.id),
                        type = PostingType.GENERAL,
                        applicationMethod = ApplicationMethod.INTERNAL,
                        title = "백엔드 개발자",
                        status = JobStatus.PUBLISHED,
                    ),
                )
            jobId = requireNotNull(job.id)
            val member =
                memberRepository.saveAndFlush(
                    Member(
                        oauthProvider = OAuthProvider.DG,
                        oauthSubject = "subject-1",
                        email = "student@example.com",
                        status = MemberStatus.ACTIVE,
                        profilePublic = true,
                    ),
                )
            memberId = requireNotNull(member.id)
        }

        private fun recommendationOf(
            rank: Int = 1,
            score: Int = 87,
        ) = Recommendation(
            memberId = memberId,
            jobId = jobId,
            recommendationDate = today,
            score = BigDecimal(score),
            suitability = SuitabilityLevel.HIGHLY_RECOMMENDED,
            rank = rank,
            algorithmVersion = 1,
        ).apply { reasons = """[{"type":"REQUIRED_SKILL_MATCH","matchedCount":3,"totalCount":4}]""" }

        @Test
        fun `score, rank, algorithmVersion, reasons를 저장하고 그대로 조회한다`() {
            recommendationRepository.saveAndFlush(recommendationOf())

            val saved =
                recommendationRepository.findAllByMemberIdAndRecommendationDate(
                    memberId,
                    today,
                    null,
                    firstPage,
                )

            assertThat(saved.content).hasSize(1)
            assertThat(saved.content[0].score).isEqualByComparingTo(BigDecimal(87))
            assertThat(saved.content[0].rank).isEqualTo(1)
            assertThat(saved.content[0].algorithmVersion).isEqualTo(1)
            assertThat(saved.content[0].suitability).isEqualTo(SuitabilityLevel.HIGHLY_RECOMMENDED)
            assertThat(saved.content[0].reasons).contains("REQUIRED_SKILL_MATCH")
            assertThat(saved.content[0].createdAt).isNotNull()
        }

        @Test
        fun `suitabilityLevel Filter를 지정하면 일치하는 결과만 조회한다`() {
            recommendationRepository.saveAndFlush(recommendationOf())

            val matched =
                recommendationRepository.findAllByMemberIdAndRecommendationDate(
                    memberId,
                    today,
                    SuitabilityLevel.HIGHLY_RECOMMENDED,
                    firstPage,
                )
            val unmatched =
                recommendationRepository.findAllByMemberIdAndRecommendationDate(
                    memberId,
                    today,
                    SuitabilityLevel.NORMAL,
                    firstPage,
                )

            assertThat(matched.content).hasSize(1)
            assertThat(unmatched.content).isEmpty()
        }

        @Test
        fun `findMaxCreatedAtByMemberIdAndRecommendationDate는 오늘자 결과 중 가장 최근 생성 시각을 반환한다`() {
            recommendationRepository.saveAndFlush(recommendationOf())

            val generatedAt = recommendationRepository.findMaxCreatedAtByMemberIdAndRecommendationDate(memberId, today)

            assertThat(generatedAt).isNotNull()
        }

        @Test
        fun `오늘자 결과가 없으면 findMaxCreatedAtByMemberIdAndRecommendationDate는 null이다`() {
            val generatedAt = recommendationRepository.findMaxCreatedAtByMemberIdAndRecommendationDate(memberId, today)

            assertThat(generatedAt).isNull()
        }

        @Test
        fun `같은 회원+공고+날짜 조합은 Unique 제약으로 중복 저장할 수 없다`() {
            recommendationRepository.saveAndFlush(recommendationOf(rank = 1))

            assertThatThrownBy { recommendationRepository.saveAndFlush(recommendationOf(rank = 2)) }
                .isInstanceOf(DataIntegrityViolationException::class.java)
        }

        @Test
        fun `deleteAllByMemberIdAndRecommendationDate는 그 회원의 그날 결과만 지운다`() {
            val otherJob =
                jobRepository.saveAndFlush(
                    Job(
                        companyId = jobRepository.findById(jobId).get().companyId,
                        type = PostingType.GENERAL,
                        applicationMethod = ApplicationMethod.INTERNAL,
                        title = "다른 공고",
                        status = JobStatus.PUBLISHED,
                    ),
                )
            recommendationRepository.saveAndFlush(recommendationOf())
            recommendationRepository.saveAndFlush(
                Recommendation(
                    memberId = memberId,
                    jobId = requireNotNull(otherJob.id),
                    recommendationDate = today.minusDays(1),
                    score = BigDecimal(50),
                    suitability = SuitabilityLevel.NORMAL,
                    rank = 1,
                    algorithmVersion = 1,
                ),
            )

            recommendationRepository.deleteAllByMemberIdAndRecommendationDate(memberId, today)
            recommendationRepository.flush()

            assertThat(
                recommendationRepository
                    .findAllByMemberIdAndRecommendationDate(
                        memberId,
                        today,
                        null,
                        firstPage,
                    ).content,
            ).isEmpty()
            assertThat(recommendationRepository.findAll()).hasSize(1)
        }

        @Test
        fun `exclusion이 있는 MemberJobPreference의 jobId만 관심 없음 목록으로 조회한다`() {
            memberJobPreferenceRepository.saveAndFlush(
                MemberJobPreference(memberId = memberId, jobId = jobId, bookmarked = false).apply {
                    exclusion = ExclusionType.THIS_JOB
                },
            )

            val excludedJobIds = memberJobPreferenceRepository.findExcludedJobIdsByMemberId(memberId)

            assertThat(excludedJobIds).containsExactly(jobId)
        }

        @Test
        fun `exclusion이 없는(북마크만 한) MemberJobPreference는 관심 없음 목록에 없지만 북마크 목록에는 있다`() {
            memberJobPreferenceRepository.saveAndFlush(
                MemberJobPreference(memberId = memberId, jobId = jobId, bookmarked = true),
            )

            assertThat(memberJobPreferenceRepository.findExcludedJobIdsByMemberId(memberId)).isEmpty()
            assertThat(memberJobPreferenceRepository.findBookmarkedJobIdsByMemberId(memberId)).containsExactly(jobId)
            assertThat(
                memberJobPreferenceRepository.findBookmarkedJobIdsByMemberIdAndJobIdIn(memberId, setOf(jobId)),
            ).containsExactly(jobId)
        }

        // ---------- R3: recommendation_preferences (Issue #152, V23 Migration) ----------

        @Test
        fun `회원의 추천 설정을 저장하고 memberId로 조회한다`() {
            recommendationPreferenceRepository.saveAndFlush(
                RecommendationPreference(memberId = memberId, enabled = true),
            )

            val found = recommendationPreferenceRepository.findByMemberId(memberId)

            assertThat(found).isNotNull()
            assertThat(found?.enabled).isTrue()
            assertThat(found?.updatedAt).isNotNull()
        }

        @Test
        fun `설정한 적 없는 회원은 조회 결과가 없다`() {
            val found = recommendationPreferenceRepository.findByMemberId(memberId)

            assertThat(found).isNull()
        }

        @Test
        fun `같은 회원의 설정 Row를 두 번 만들 수 없다(memberId Unique 제약)`() {
            recommendationPreferenceRepository.saveAndFlush(
                RecommendationPreference(memberId = memberId, enabled = true),
            )

            assertThatThrownBy {
                recommendationPreferenceRepository.saveAndFlush(
                    RecommendationPreference(memberId = memberId, enabled = false),
                )
            }.isInstanceOf(DataIntegrityViolationException::class.java)
        }

        @Test
        fun `enabled 값을 갱신하면 updatedAt도 함께 바뀐다`() {
            val saved =
                recommendationPreferenceRepository.saveAndFlush(
                    RecommendationPreference(memberId = memberId, enabled = true),
                )
            val firstUpdatedAt = requireNotNull(saved.updatedAt)

            saved.enabled = false
            val updated = recommendationPreferenceRepository.saveAndFlush(saved)

            assertThat(updated.enabled).isFalse()
            assertThat(updated.updatedAt).isNotNull()
            assertThat(updated.id).isEqualTo(saved.id)
            // memberId Unique 제약 위반 없이(같은 Row를 갱신) 저장됐는지도 함께 확인한다.
            assertThat(recommendationPreferenceRepository.count()).isEqualTo(1)
        }

        @Test
        fun `upsert는 설정 Row가 없으면 새로 만든다`() {
            recommendationPreferenceRepository.upsert(memberId, true)

            val found = requireNotNull(recommendationPreferenceRepository.findByMemberId(memberId))
            assertThat(found.enabled).isTrue()
            assertThat(found.updatedAt).isNotNull()
        }

        @Test
        fun `upsert는 이미 설정 Row가 있으면 값을 갱신하고 새 Row를 만들지 않는다`() {
            recommendationPreferenceRepository.saveAndFlush(
                RecommendationPreference(memberId = memberId, enabled = true),
            )

            recommendationPreferenceRepository.upsert(memberId, false)

            val found = requireNotNull(recommendationPreferenceRepository.findByMemberId(memberId))
            assertThat(found.enabled).isFalse()
            assertThat(recommendationPreferenceRepository.count()).isEqualTo(1)
        }

        @Test
        fun `upsert를 같은 회원에게 여러 번 호출해도 Unique 제약 위반 없이 멱등하게 동작한다`() {
            // find-then-save 대신 upsert 하나로 처리하는 이유가 되는 시나리오다(코드리뷰 반영,
            // RecommendationPreferenceRepository.upsert KDoc 참고) -- 동시 요청을 Thread로
            // 정확히 재현하지는 않지만, 반복 호출 자체가 예외 없이 계속 성공하는지 확인한다.
            recommendationPreferenceRepository.upsert(memberId, true)
            recommendationPreferenceRepository.upsert(memberId, true)
            recommendationPreferenceRepository.upsert(memberId, false)

            assertThat(recommendationPreferenceRepository.count()).isEqualTo(1)
            assertThat(requireNotNull(recommendationPreferenceRepository.findByMemberId(memberId)).enabled).isFalse()
        }

        // ---------- R3: 관심 없음 설정 시 Recommendation 즉시 제거 ----------

        @Test
        fun `deleteAllByMemberIdAndJobId는 그 회원의 그 공고 Recommendation만 지운다`() {
            val otherJob =
                jobRepository.saveAndFlush(
                    Job(
                        companyId = jobRepository.findById(jobId).get().companyId,
                        type = PostingType.GENERAL,
                        applicationMethod = ApplicationMethod.INTERNAL,
                        title = "다른 공고",
                        status = JobStatus.PUBLISHED,
                    ),
                )
            recommendationRepository.saveAndFlush(recommendationOf())
            recommendationRepository.saveAndFlush(
                Recommendation(
                    memberId = memberId,
                    jobId = requireNotNull(otherJob.id),
                    recommendationDate = today,
                    score = BigDecimal(50),
                    suitability = SuitabilityLevel.NORMAL,
                    rank = 2,
                    algorithmVersion = 1,
                ),
            )

            recommendationRepository.deleteAllByMemberIdAndJobId(memberId, jobId)
            recommendationRepository.flush()

            val remaining =
                recommendationRepository.findAllByMemberIdAndRecommendationDate(
                    memberId,
                    today,
                    null,
                    firstPage,
                )
            assertThat(remaining.content).hasSize(1)
            assertThat(remaining.content[0].jobId).isEqualTo(otherJob.id)
        }

        @Test
        fun `다른 회원의 같은 Job Recommendation은 영향받지 않는다`() {
            val otherMember =
                memberRepository.saveAndFlush(
                    Member(
                        oauthProvider = OAuthProvider.DG,
                        oauthSubject = "subject-2",
                        email = "other-student@example.com",
                        status = MemberStatus.ACTIVE,
                        profilePublic = true,
                    ),
                )
            val otherMemberId = requireNotNull(otherMember.id)
            recommendationRepository.saveAndFlush(recommendationOf())
            recommendationRepository.saveAndFlush(
                Recommendation(
                    memberId = otherMemberId,
                    jobId = jobId,
                    recommendationDate = today,
                    score = BigDecimal(50),
                    suitability = SuitabilityLevel.NORMAL,
                    rank = 1,
                    algorithmVersion = 1,
                ),
            )

            recommendationRepository.deleteAllByMemberIdAndJobId(memberId, jobId)
            recommendationRepository.flush()

            assertThat(
                recommendationRepository
                    .findAllByMemberIdAndRecommendationDate(
                        memberId,
                        today,
                        null,
                        firstPage,
                    ).content,
            ).isEmpty()
            assertThat(
                recommendationRepository
                    .findAllByMemberIdAndRecommendationDate(
                        otherMemberId,
                        today,
                        null,
                        firstPage,
                    ).content,
            ).hasSize(1)
        }

        // ---------- Notion 계약 정합성(Issue #155, V24 Migration): 관심 없음 설정/해제 ----------

        @Test
        fun `관심 없음을 설정한 뒤 해제하면 Preference Row가 사라진다`() {
            val saved =
                memberJobPreferenceRepository.saveAndFlush(
                    MemberJobPreference(memberId = memberId, jobId = jobId).apply {
                        exclusion = ExclusionType.THIS_JOB
                    },
                )
            assertThat(memberJobPreferenceRepository.findExcludedJobIdsByMemberId(memberId)).containsExactly(jobId)

            val preference =
                requireNotNull(memberJobPreferenceRepository.findById(requireNotNull(saved.id)).orElse(null))
            preference.exclusion = null
            memberJobPreferenceRepository.delete(preference)
            memberJobPreferenceRepository.flush()

            assertThat(memberJobPreferenceRepository.findExcludedJobIdsByMemberId(memberId)).isEmpty()
            assertThat(memberJobPreferenceRepository.findById(requireNotNull(saved.id)).isPresent).isFalse()
        }

        @Test
        fun `member_job_preferences는 Surrogate id로 식별되고 exclusionCreatedAt을 저장한다`() {
            val saved =
                memberJobPreferenceRepository.saveAndFlush(
                    MemberJobPreference(memberId = memberId, jobId = jobId).apply {
                        exclusion = ExclusionType.THIS_JOB
                        exclusionCreatedAt = LocalDateTime.now()
                    },
                )

            assertThat(saved.id).isNotNull()
            assertThat(saved.exclusionCreatedAt).isNotNull()

            val found = memberJobPreferenceRepository.findByIdAndMemberId(requireNotNull(saved.id), memberId)
            assertThat(found).isNotNull()
        }

        @Test
        fun `findByIdAndMemberId는 다른 회원의 exclusionId를 조회하면 null이다(소유권 검증)`() {
            val saved =
                memberJobPreferenceRepository.saveAndFlush(
                    MemberJobPreference(memberId = memberId, jobId = jobId).apply {
                        exclusion = ExclusionType.THIS_JOB
                    },
                )
            val otherMember =
                memberRepository.saveAndFlush(
                    Member(
                        oauthProvider = OAuthProvider.DG,
                        oauthSubject = "subject-owner-check",
                        email = "owner-check@example.com",
                        status = MemberStatus.ACTIVE,
                        profilePublic = true,
                    ),
                )

            val found =
                memberJobPreferenceRepository.findByIdAndMemberId(
                    requireNotNull(saved.id),
                    requireNotNull(otherMember.id),
                )

            assertThat(found).isNull()
        }

        @Test
        fun `existsByMemberIdAndJobIdAndExclusionIsNotNull은 관심 없음이 이미 있으면 true다`() {
            memberJobPreferenceRepository.saveAndFlush(
                MemberJobPreference(memberId = memberId, jobId = jobId).apply {
                    exclusion = ExclusionType.THIS_JOB
                },
            )

            assertThat(
                memberJobPreferenceRepository.existsByMemberIdAndJobIdAndExclusionIsNotNull(memberId, jobId),
            ).isTrue()
        }

        @Test
        fun `existsByMemberIdAndJobIdAndExclusionIsNotNull은 북마크만 있으면 false다`() {
            memberJobPreferenceRepository.saveAndFlush(
                MemberJobPreference(memberId = memberId, jobId = jobId, bookmarked = true),
            )

            assertThat(
                memberJobPreferenceRepository.existsByMemberIdAndJobIdAndExclusionIsNotNull(memberId, jobId),
            ).isFalse()
        }

        @Test
        fun `findAllExclusionsByMemberId는 본인의 관심 없음만 exclusionType으로 필터링해 조회한다`() {
            val otherJob =
                jobRepository.saveAndFlush(
                    Job(
                        companyId = jobRepository.findById(jobId).get().companyId,
                        type = PostingType.GENERAL,
                        applicationMethod = ApplicationMethod.INTERNAL,
                        title = "다른 공고",
                        status = JobStatus.PUBLISHED,
                    ),
                )
            memberJobPreferenceRepository.saveAndFlush(
                MemberJobPreference(memberId = memberId, jobId = jobId).apply {
                    exclusion = ExclusionType.THIS_JOB
                },
            )
            memberJobPreferenceRepository.saveAndFlush(
                MemberJobPreference(memberId = memberId, jobId = requireNotNull(otherJob.id)).apply {
                    exclusion = ExclusionType.SIMILAR_JOBS
                },
            )
            val otherMember =
                memberRepository.saveAndFlush(
                    Member(
                        oauthProvider = OAuthProvider.DG,
                        oauthSubject = "subject-exclusion-list",
                        email = "exclusion-list@example.com",
                        status = MemberStatus.ACTIVE,
                        profilePublic = true,
                    ),
                )
            memberJobPreferenceRepository.saveAndFlush(
                MemberJobPreference(memberId = requireNotNull(otherMember.id), jobId = jobId).apply {
                    exclusion = ExclusionType.THIS_JOB
                },
            )

            val all = memberJobPreferenceRepository.findAllExclusionsByMemberId(memberId, null, firstPage)
            val onlySimilar =
                memberJobPreferenceRepository.findAllExclusionsByMemberId(
                    memberId,
                    ExclusionType.SIMILAR_JOBS,
                    firstPage,
                )

            assertThat(all.totalElements).isEqualTo(2)
            assertThat(onlySimilar.content).hasSize(1)
            assertThat(onlySimilar.content[0].jobId).isEqualTo(otherJob.id)
        }

        @Test
        fun `member_job_preferences는 Surrogate id를 PK로 쓰고 member_id와 job_id 조합은 UNIQUE다`() {
            memberJobPreferenceRepository.saveAndFlush(MemberJobPreference(memberId = memberId, jobId = jobId))

            assertThatThrownBy {
                memberJobPreferenceRepository.saveAndFlush(MemberJobPreference(memberId = memberId, jobId = jobId))
            }.isInstanceOf(DataIntegrityViolationException::class.java)
        }

        // ---------- R4: recommendation_generation_states (Issue #160, V25 Migration) ----------

        @Test
        fun `같은 회원의 Generation State Row를 두 번 만들 수 없다(memberId Unique 제약)`() {
            recommendationGenerationStateRepository.saveAndFlush(
                RecommendationGenerationState(memberId = memberId, status = RecommendationGenerationStatus.READY),
            )

            assertThatThrownBy {
                recommendationGenerationStateRepository.saveAndFlush(
                    RecommendationGenerationState(memberId = memberId, status = RecommendationGenerationStatus.EMPTY),
                )
            }.isInstanceOf(DataIntegrityViolationException::class.java)
        }

        @Test
        fun `markGenerating은 Row가 없으면 새로 만들고 있으면 GENERATING으로 갱신한다`() {
            recommendationGenerationStateRepository.markGenerating(memberId)
            val created = requireNotNull(recommendationGenerationStateRepository.findByMemberId(memberId))
            assertThat(created.status).isEqualTo(RecommendationGenerationStatus.GENERATING)

            recommendationGenerationStateRepository.markCompleted(memberId, "READY", LocalDateTime.now())
            recommendationGenerationStateRepository.markGenerating(memberId)

            val updated = requireNotNull(recommendationGenerationStateRepository.findByMemberId(memberId))
            assertThat(updated.status).isEqualTo(RecommendationGenerationStatus.GENERATING)
            assertThat(recommendationGenerationStateRepository.count()).isEqualTo(1)
        }

        @Test
        fun `markCompleted는 status와 generatedAt을 갱신하고 이전 실패 시각은 건드리지 않는다`() {
            val failedAt = LocalDateTime.now().minusDays(1)
            recommendationGenerationStateRepository.markFailed(memberId, failedAt)

            val generatedAt = LocalDateTime.now()
            recommendationGenerationStateRepository.markCompleted(memberId, "READY", generatedAt)

            val found = requireNotNull(recommendationGenerationStateRepository.findByMemberId(memberId))
            assertThat(found.status).isEqualTo(RecommendationGenerationStatus.READY)
            assertThat(found.generatedAt).isEqualToIgnoringNanos(generatedAt)
            assertThat(found.lastFailureAt).isEqualToIgnoringNanos(failedAt)
        }

        @Test
        fun `markFailed는 status를 FAILED로 갱신하고 이전 성공 시각은 건드리지 않는다`() {
            val generatedAt = LocalDateTime.now().minusDays(1)
            recommendationGenerationStateRepository.markCompleted(memberId, "READY", generatedAt)

            val failedAt = LocalDateTime.now()
            recommendationGenerationStateRepository.markFailed(memberId, failedAt)

            val found = requireNotNull(recommendationGenerationStateRepository.findByMemberId(memberId))
            assertThat(found.status).isEqualTo(RecommendationGenerationStatus.FAILED)
            assertThat(found.lastFailureAt).isEqualToIgnoringNanos(failedAt)
            assertThat(found.generatedAt).isEqualToIgnoringNanos(generatedAt)
        }

        @Test
        fun `설정한 적 없는 회원은 Generation State 조회 결과가 없다`() {
            assertThat(recommendationGenerationStateRepository.findByMemberId(memberId)).isNull()
        }

        // ---------- R6: SIMILAR_JOBS 유사 공고 확장(Issue #167) ----------

        @Test
        fun `findJobIdsByMemberIdAndExclusionType은 SIMILAR_JOBS만 좁혀 조회한다`() {
            val otherJob =
                jobRepository.saveAndFlush(
                    Job(
                        companyId = jobRepository.findById(jobId).get().companyId,
                        type = PostingType.GENERAL,
                        applicationMethod = ApplicationMethod.INTERNAL,
                        title = "다른 공고",
                        status = JobStatus.PUBLISHED,
                    ),
                )
            memberJobPreferenceRepository.saveAndFlush(
                MemberJobPreference(memberId = memberId, jobId = jobId).apply { exclusion = ExclusionType.THIS_JOB },
            )
            memberJobPreferenceRepository.saveAndFlush(
                MemberJobPreference(memberId = memberId, jobId = requireNotNull(otherJob.id)).apply {
                    exclusion = ExclusionType.SIMILAR_JOBS
                },
            )

            val similarJobsSourceIds =
                memberJobPreferenceRepository.findJobIdsByMemberIdAndExclusionType(memberId, ExclusionType.SIMILAR_JOBS)

            assertThat(similarJobsSourceIds).containsExactly(otherJob.id)
        }

        @Test
        fun `findJobIdsByMemberIdAndRecommendationDate는 그 회원의 오늘자 Recommendation jobId를 반환한다`() {
            val otherJob =
                jobRepository.saveAndFlush(
                    Job(
                        companyId = jobRepository.findById(jobId).get().companyId,
                        type = PostingType.GENERAL,
                        applicationMethod = ApplicationMethod.INTERNAL,
                        title = "다른 공고",
                        status = JobStatus.PUBLISHED,
                    ),
                )
            recommendationRepository.saveAndFlush(recommendationOf())
            recommendationRepository.saveAndFlush(
                Recommendation(
                    memberId = memberId,
                    jobId = requireNotNull(otherJob.id),
                    recommendationDate = today,
                    score = BigDecimal(50),
                    suitability = SuitabilityLevel.NORMAL,
                    rank = 2,
                    algorithmVersion = 1,
                ),
            )
            // 어제자 결과는 대상이 아니다.
            recommendationRepository.saveAndFlush(
                Recommendation(
                    memberId = memberId,
                    jobId = jobId,
                    recommendationDate = today.minusDays(1),
                    score = BigDecimal(50),
                    suitability = SuitabilityLevel.NORMAL,
                    rank = 1,
                    algorithmVersion = 1,
                ),
            )

            val jobIds = recommendationRepository.findJobIdsByMemberIdAndRecommendationDate(memberId, today)

            assertThat(jobIds).containsExactlyInAnyOrder(jobId, otherJob.id)
        }

        @Test
        fun `deleteAllByMemberIdAndJobIdIn은 지정된 여러 Job의 그 회원 Recommendation만 지운다`() {
            val similarJob =
                jobRepository.saveAndFlush(
                    Job(
                        companyId = jobRepository.findById(jobId).get().companyId,
                        type = PostingType.GENERAL,
                        applicationMethod = ApplicationMethod.INTERNAL,
                        title = "유사 공고",
                        status = JobStatus.PUBLISHED,
                    ),
                )
            val keptJob =
                jobRepository.saveAndFlush(
                    Job(
                        companyId = jobRepository.findById(jobId).get().companyId,
                        type = PostingType.GENERAL,
                        applicationMethod = ApplicationMethod.INTERNAL,
                        title = "유지되는 공고",
                        status = JobStatus.PUBLISHED,
                    ),
                )
            val otherMember =
                memberRepository.saveAndFlush(
                    Member(
                        oauthProvider = OAuthProvider.DG,
                        oauthSubject = "subject-similar-jobs-delete",
                        email = "similar-jobs-delete@example.com",
                        status = MemberStatus.ACTIVE,
                        profilePublic = true,
                    ),
                )
            val otherMemberId = requireNotNull(otherMember.id)
            recommendationRepository.saveAndFlush(recommendationOf()) // memberId, jobId(기준 Job)
            recommendationRepository.saveAndFlush(
                Recommendation(
                    memberId = memberId,
                    jobId = requireNotNull(similarJob.id),
                    recommendationDate = today,
                    score = BigDecimal(50),
                    suitability = SuitabilityLevel.NORMAL,
                    rank = 2,
                    algorithmVersion = 1,
                ),
            )
            recommendationRepository.saveAndFlush(
                Recommendation(
                    memberId = memberId,
                    jobId = requireNotNull(keptJob.id),
                    recommendationDate = today,
                    score = BigDecimal(50),
                    suitability = SuitabilityLevel.NORMAL,
                    rank = 3,
                    algorithmVersion = 1,
                ),
            )
            // 다른 회원의 같은 similarJob Recommendation은 영향받지 않아야 한다.
            recommendationRepository.saveAndFlush(
                Recommendation(
                    memberId = otherMemberId,
                    jobId = requireNotNull(similarJob.id),
                    recommendationDate = today,
                    score = BigDecimal(50),
                    suitability = SuitabilityLevel.NORMAL,
                    rank = 1,
                    algorithmVersion = 1,
                ),
            )

            recommendationRepository.deleteAllByMemberIdAndJobIdIn(
                memberId,
                setOf(jobId, requireNotNull(similarJob.id)),
            )
            recommendationRepository.flush()

            assertThat(recommendationRepository.findJobIdsByMemberIdAndRecommendationDate(memberId, today))
                .containsExactly(keptJob.id)
            assertThat(recommendationRepository.findJobIdsByMemberIdAndRecommendationDate(otherMemberId, today))
                .containsExactly(similarJob.id)
        }

        companion object {
            @Container
            @ServiceConnection
            @JvmStatic
            val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18.4-alpine"))
        }
    }
