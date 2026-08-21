package team.inreok.getiserver.domain.application

import com.redis.testcontainers.RedisContainer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.data.domain.Pageable
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import team.inreok.getiserver.domain.application.entity.JobApplication
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus
import team.inreok.getiserver.domain.application.repository.JobApplicationRepository
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
import java.util.UUID

/**
 * 지원자 자료 일괄 다운로드의 `applicationIds` 선택 필터(Issue #203)가 실제 PostgreSQL 위에서
 * 의도한 대로 동작하는지 확인한다. Mock 기반
 * [team.inreok.getiserver.domain.application.service.impl.JobApplicationExportServiceImplTest]는
 * `JobApplicationRepository.search()` 자체를 Mockito로 Stub 처리하므로, `hasApplicationIds`/
 * `applicationIds` JPQL 조건의 실제 Binding과 기존 `:jobId` 조건과의 AND 결합은 이 Test로 실측한다
 * (`JobApplicationAdminListFilterIntegrationTest`와 동일한 이유로 Testcontainers 실제 DB를 사용).
 *
 * `JobApplicationExportServiceImpl.buildExportEntries`는 File 도메인(Snapshot 첨부파일 조회)까지
 * 엮여 있어 그 전체 경로 대신, 이 Issue가 실제로 바꾼 대상인 `JobApplicationRepository.search()`를
 * 직접 호출해 JPQL 조건만 검증한다.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "app.jwt.secret=job-application-export-application-ids-filter-integration-test-only-jwt-secret-value",
        "app.jwt.access-token-expiration-seconds=1800",
        "app.jwt.refresh-token-expiration-seconds=1209600",
        "app.file.storage.bucket=geti-integration-test",
        "app.file.storage.region=us-east-1",
        "app.file.storage.access-key=integration-test-only-access-key",
        "app.file.storage.secret-key=integration-test-only-secret-key",
    ],
)
class JobApplicationExportApplicationIdsFilterIntegrationTest {
    @Autowired
    private lateinit var jobApplicationRepository: JobApplicationRepository

    @Autowired
    private lateinit var jobRepository: JobRepository

    @Autowired
    private lateinit var companyRepository: CompanyRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Test
    fun `applicationIds를 지정하면 선택된 지원서만 반환한다`() {
        val jobId = createJob()
        val selected = createApplication(jobId)
        createApplication(jobId)

        val result = search(jobId, hasApplicationIds = true, applicationIds = setOf(selected))

        assertThat(result.content.map { it.id }).containsExactly(selected)
    }

    // JPQL의 :hasApplicationIds/:applicationIds 조건은 기존 :jobId 조건과 AND로 결합된다
    // (JobApplicationRepository.search KDoc 참고). 다른 공고 소속 지원서 ID를 applicationIds에
    // 섞어도 :jobId 조건에서 이미 걸러지는지를 실제 DB로 확인한다(Issue #203 정책 확정: 오류로
    // 처리하지 않고 조용히 무시).
    @Test
    fun `다른 공고 소속 applicationId는 jobId 조건과의 AND 결합으로 결과에서 빠진다`() {
        val jobId = createJob()
        val otherJobId = createJob()
        val ownApplication = createApplication(jobId)
        val otherJobApplication = createApplication(otherJobId)

        val result =
            search(jobId, hasApplicationIds = true, applicationIds = setOf(ownApplication, otherJobApplication))

        assertThat(result.content.map { it.id }).containsExactly(ownApplication)
    }

    @Test
    fun `hasApplicationIds가 false면 applicationIds 값과 무관하게 전체 지원자를 반환한다`() {
        val jobId = createJob()
        val first = createApplication(jobId)
        val second = createApplication(jobId)

        // applicationIds에 실제로 존재하지 않는 값(999L)을 넣어도 hasApplicationIds가 false면
        // 이 조건 자체가 평가되지 않아야 한다(하위 호환 -- 이 Issue #203 이전 호출부와 동일한 경로).
        val result = search(jobId, hasApplicationIds = false, applicationIds = setOf(999L))

        assertThat(result.content.map { it.id }).containsExactlyInAnyOrder(first, second)
    }

    private fun search(
        jobId: Long,
        hasApplicationIds: Boolean,
        applicationIds: Set<Long>,
    ) = jobApplicationRepository.search(
        jobId = jobId,
        status = null,
        hasApplicantName = false,
        applicantName = "",
        cohort = null,
        department = null,
        hasJobFilter = false,
        jobIds = emptySet(),
        hasApplicationIds = hasApplicationIds,
        applicationIds = applicationIds,
        pageable = Pageable.unpaged(),
    )

    private fun createMember(subject: String): Long {
        val member =
            memberRepository.saveAndFlush(
                Member(
                    oauthProvider = OAuthProvider.DG,
                    oauthSubject = "export-filter-it-$subject-${UUID.randomUUID()}",
                    email = "export-filter-it-$subject-${UUID.randomUUID()}@example.com",
                    status = MemberStatus.ACTIVE,
                ),
            )
        return requireNotNull(member.id)
    }

    private fun createJob(): Long {
        val company =
            companyRepository.saveAndFlush(
                Company(name = "applicationIds 필터 Test 기업 ${UUID.randomUUID()}", type = CompanyType.GENERAL),
            )
        val job =
            jobRepository.saveAndFlush(
                Job(
                    companyId = requireNotNull(company.id),
                    type = PostingType.GENERAL,
                    applicationMethod = ApplicationMethod.INTERNAL,
                    title = "applicationIds 필터 Test 공고 ${UUID.randomUUID()}",
                    status = JobStatus.PUBLISHED,
                ),
            )
        return requireNotNull(job.id)
    }

    private fun createApplication(jobId: Long): Long {
        val application =
            jobApplicationRepository.saveAndFlush(
                JobApplication(
                    jobId = jobId,
                    applicantMemberId = createMember("applicant"),
                    attemptNumber = 1,
                    contactEmail = "applicant-${UUID.randomUUID()}@example.com",
                    answers = "[]",
                    status = JobApplicationStatus.SUBMITTED,
                ).apply {
                    applicantName = "지원자 ${UUID.randomUUID()}"
                },
            )
        return requireNotNull(application.id)
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18.4-alpine"))

        @Container
        @ServiceConnection
        @JvmStatic
        val redis = RedisContainer(DockerImageName.parse("redis:8.8.1-alpine"))
    }
}
