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
import team.inreok.getiserver.domain.recommendation.entity.MemberJobPreferenceId
import team.inreok.getiserver.domain.recommendation.entity.Recommendation
import team.inreok.getiserver.domain.recommendation.entity.type.ExclusionType
import team.inreok.getiserver.domain.recommendation.entity.type.SuitabilityLevel
import team.inreok.getiserver.domain.recommendation.repository.MemberJobPreferenceRepository
import team.inreok.getiserver.domain.recommendation.repository.RecommendationRepository
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Recommendation R2 Persistence를 실제 PostgreSQL로 검증한다(Issue #148). Service Test(Mock
 * Repository)로는 V22 Migration이 추가한 Column, `uk_recommendations_member_job_date` Unique
 * 제약, JSONB `reasons` 저장/조회를 확인할 수 없다.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration::class)
class RecommendationPersistenceIntegrationTest
    @Autowired
    constructor(
        private val recommendationRepository: RecommendationRepository,
        private val memberJobPreferenceRepository: MemberJobPreferenceRepository,
        private val memberRepository: MemberRepository,
        private val jobRepository: JobRepository,
        private val companyRepository: CompanyRepository,
    ) {
        private var memberId: Long = 0
        private var jobId: Long = 0
        private val today: LocalDate = LocalDate.now()

        @BeforeEach
        fun setUp() {
            recommendationRepository.deleteAll()
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

            val saved = recommendationRepository.findAllByMemberIdAndRecommendationDateOrderByRank(memberId, today)

            assertThat(saved).hasSize(1)
            assertThat(saved[0].score).isEqualByComparingTo(BigDecimal(87))
            assertThat(saved[0].rank).isEqualTo(1)
            assertThat(saved[0].algorithmVersion).isEqualTo(1)
            assertThat(saved[0].suitability).isEqualTo(SuitabilityLevel.HIGHLY_RECOMMENDED)
            assertThat(saved[0].reasons).contains("REQUIRED_SKILL_MATCH")
            assertThat(saved[0].createdAt).isNotNull()
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
                recommendationRepository.findAllByMemberIdAndRecommendationDateOrderByRank(memberId, today),
            ).isEmpty()
            assertThat(recommendationRepository.findAll()).hasSize(1)
        }

        @Test
        fun `exclusion이 있는 MemberJobPreference의 jobId만 관심 없음 목록으로 조회한다`() {
            memberJobPreferenceRepository.saveAndFlush(
                MemberJobPreference(MemberJobPreferenceId(memberId, jobId), bookmarked = false).apply {
                    exclusion = ExclusionType.THIS_JOB
                },
            )

            val excludedJobIds = memberJobPreferenceRepository.findExcludedJobIdsByMemberId(memberId)

            assertThat(excludedJobIds).containsExactly(jobId)
        }

        @Test
        fun `exclusion이 없는(북마크만 한) MemberJobPreference는 관심 없음 목록에 없다`() {
            memberJobPreferenceRepository.saveAndFlush(
                MemberJobPreference(MemberJobPreferenceId(memberId, jobId), bookmarked = true),
            )

            val excludedJobIds = memberJobPreferenceRepository.findExcludedJobIdsByMemberId(memberId)

            assertThat(excludedJobIds).isEmpty()
        }

        companion object {
            @Container
            @ServiceConnection
            @JvmStatic
            val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18.4-alpine"))
        }
    }
