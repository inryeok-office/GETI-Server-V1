package team.inreok.getiserver.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
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
import team.inreok.getiserver.domain.job.query.JobBookmarkQuery
import team.inreok.getiserver.domain.job.query.JobBookmarkSort
import team.inreok.getiserver.domain.job.repository.JobRepository
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.recommendation.entity.MemberJobPreference
import team.inreok.getiserver.domain.recommendation.repository.MemberJobPreferenceRepository
import java.time.LocalDateTime

/** 북마크 목록이 MemberJobPreference와 Job 필터를 DB에서 함께 적용하는지 검증한다(Issue #170). */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration::class)
class JobBookmarkQueryIntegrationTest
    @Autowired
    constructor(
        private val jobRepository: JobRepository,
        private val companyRepository: CompanyRepository,
        private val memberRepository: MemberRepository,
        private val memberJobPreferenceRepository: MemberJobPreferenceRepository,
    ) {
        private var memberId: Long = 0L
        private val pageable = PageRequest.of(0, 1)

        @BeforeEach
        fun setUp() {
            memberJobPreferenceRepository.deleteAll()
            memberJobPreferenceRepository.flush()
            jobRepository.deleteAll()
            jobRepository.flush()
            companyRepository.deleteAll()
            companyRepository.flush()
            memberRepository.deleteAll()
            memberRepository.flush()
            memberId = member("bookmark-owner").id!!
        }

        @Test
        fun `본인 북마크만 공개 공고 조건과 함께 DB 페이지네이션한다`() {
            val generalCompany = company(CompanyType.GENERAL)
            val publicCompany = company(CompanyType.PUBLIC_ENTERPRISE)
            val first =
                job(
                    generalCompany,
                    title = "백엔드 개발 인턴",
                    publishedAt = LocalDateTime.of(2026, 8, 2, 0, 0),
                )
            val second =
                job(
                    publicCompany,
                    title = "프론트엔드 개발 인턴",
                    publishedAt = LocalDateTime.of(2026, 8, 1, 0, 0),
                )
            val draft = job(generalCompany, title = "임시저장 백엔드", status = JobStatus.DRAFT)
            val otherMemberJob = job(generalCompany, title = "다른 회원 공고")

            bookmark(first.id!!, memberId)
            bookmark(second.id!!, memberId)
            bookmark(draft.id!!, memberId)
            val otherMemberId = member("other-bookmark-owner").id!!
            bookmark(otherMemberJob.id!!, otherMemberId)

            val firstPage =
                memberJobPreferenceRepository.findBookmarkedJobIdsByMemberIdAndQuery(
                    memberId,
                    JobBookmarkQuery(null, null, null, JobBookmarkSort.LATEST),
                    pageable,
                )
            val secondPage =
                memberJobPreferenceRepository.findBookmarkedJobIdsByMemberIdAndQuery(
                    memberId,
                    JobBookmarkQuery(null, null, null, JobBookmarkSort.LATEST),
                    PageRequest.of(1, 1),
                )

            assertThat(firstPage.totalElements).isEqualTo(2)
            assertThat(firstPage.content.single()).isEqualTo(first.id)
            assertThat(secondPage.content.single()).isEqualTo(second.id)
        }

        @Test
        fun `검색어 공고유형 기업유형 필터를 DB Query에 적용한다`() {
            val generalCompany = company(CompanyType.GENERAL)
            val publicCompany = company(CompanyType.PUBLIC_ENTERPRISE)
            val matching = job(generalCompany, title = "백엔드 개발 인턴", type = PostingType.GENERAL)
            val wrongCompany = job(publicCompany, title = "백엔드 개발자", type = PostingType.GENERAL)
            val wrongType = job(generalCompany, title = "백엔드 개발자", type = PostingType.MOU)

            bookmark(matching.id!!, memberId)
            bookmark(wrongCompany.id!!, memberId)
            bookmark(wrongType.id!!, memberId)

            val result =
                memberJobPreferenceRepository.findBookmarkedJobIdsByMemberIdAndQuery(
                    memberId,
                    JobBookmarkQuery("백엔드", PostingType.GENERAL, CompanyType.GENERAL.name, JobBookmarkSort.LATEST),
                    PageRequest.of(0, 20),
                )

            assertThat(result.content).containsExactly(matching.id)
            assertThat(result.totalElements).isEqualTo(1)
        }

        private fun company(type: CompanyType): Long =
            companyRepository.saveAndFlush(Company(name = "기업 ${type.name}", type = type)).id!!

        private fun member(subject: String): Member =
            memberRepository.saveAndFlush(
                Member(
                    oauthProvider = OAuthProvider.DG,
                    oauthSubject = subject,
                    email = "$subject@example.com",
                ),
            )

        private fun job(
            companyId: Long,
            title: String,
            status: JobStatus = JobStatus.PUBLISHED,
            type: PostingType = PostingType.GENERAL,
            publishedAt: LocalDateTime? = LocalDateTime.of(2026, 8, 1, 0, 0),
        ): Job =
            jobRepository.saveAndFlush(
                Job(
                    companyId = companyId,
                    type = type,
                    applicationMethod = ApplicationMethod.EXTERNAL,
                    title = title,
                    status = status,
                ).apply { this.publishedAt = publishedAt },
            )

        private fun bookmark(
            jobId: Long,
            memberId: Long,
        ) {
            memberJobPreferenceRepository.saveAndFlush(
                MemberJobPreference(memberId = memberId, jobId = jobId, bookmarked = true),
            )
        }

        companion object {
            @Container
            @ServiceConnection
            @JvmStatic
            val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18.4-alpine"))
        }
    }
