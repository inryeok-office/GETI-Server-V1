package team.inreok.getiserver.domain.application

import com.redis.testcontainers.RedisContainer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.data.domain.PageRequest
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import team.inreok.getiserver.domain.application.entity.JobApplication
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus
import team.inreok.getiserver.domain.application.repository.JobApplicationRepository
import team.inreok.getiserver.domain.application.service.JobApplicationAdminService
import team.inreok.getiserver.domain.company.entity.Company
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.company.repository.CompanyRepository
import team.inreok.getiserver.domain.job.entity.Job
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.entity.type.PostingType
import team.inreok.getiserver.domain.job.repository.JobRepository
import team.inreok.getiserver.domain.member.entity.type.DepartmentType
import java.util.UUID

/**
 * 관리자 지원자 목록 필터 확장(Issue #181)이 실제 PostgreSQL 위에서 의도한 대로 동작하는지
 * 확인한다. Mock 기반 [team.inreok.getiserver.domain.application.service.impl.JobApplicationAdminServiceImplTest]는
 * Repository/Job 도메인 배치 조회 호출 여부만 검증하므로, 실제 LIKE 대소문자 무시 검색과
 * companyId/managerMemberId/mineOnly가 Job 도메인을 거쳐 정확히 필터링되는지는 이 Test로 실측한다
 * (`JobApplicationE2EIntegrationTest`와 동일한 이유로 Testcontainers 실제 DB를 사용한다).
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "app.jwt.secret=job-application-admin-list-filter-integration-test-only-jwt-secret-value",
        "app.jwt.access-token-expiration-seconds=1800",
        "app.jwt.refresh-token-expiration-seconds=1209600",
        "app.file.storage.bucket=geti-integration-test",
        "app.file.storage.region=us-east-1",
        "app.file.storage.access-key=integration-test-only-access-key",
        "app.file.storage.secret-key=integration-test-only-secret-key",
    ],
)
class JobApplicationAdminListFilterIntegrationTest {
    @Autowired
    private lateinit var jobApplicationAdminService: JobApplicationAdminService

    @Autowired
    private lateinit var jobApplicationRepository: JobApplicationRepository

    @Autowired
    private lateinit var jobRepository: JobRepository

    @Autowired
    private lateinit var companyRepository: CompanyRepository

    @Test
    fun `applicantName은 대소문자 구분 없이 부분 검색된다`() {
        val jobId = createJob()
        createApplication(jobId, applicantName = "Hong Gildong")
        createApplication(jobId, applicantName = "Kim Cheolsu")

        val result =
            jobApplicationAdminService.list(
                jobId = null,
                status = null,
                applicantName = "gild",
                cohort = null,
                department = null,
                companyId = null,
                managerMemberId = null,
                mineOnly = false,
                requesterMemberId = 1L,
                pageable = PageRequest.of(0, 20),
            )

        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].applicantName).isEqualTo("Hong Gildong")
    }

    @Test
    fun `cohort와 department Filter는 정확히 일치하는 지원서만 반환한다`() {
        val jobId = createJob()
        createApplication(jobId, cohort = 5, department = DepartmentType.AI.name)
        createApplication(jobId, cohort = 6, department = DepartmentType.SW_DEVELOPMENT.name)

        val result =
            jobApplicationAdminService.list(
                jobId = null,
                status = null,
                applicantName = null,
                cohort = 5,
                department = DepartmentType.AI,
                companyId = null,
                managerMemberId = null,
                mineOnly = false,
                requesterMemberId = 1L,
                pageable = PageRequest.of(0, 20),
            )

        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].applicantCohort).isEqualTo(5)
    }

    @Test
    fun `companyId Filter는 해당 기업의 공고에 속한 지원서만 반환한다`() {
        val companyAId = createCompany()
        val companyBId = createCompany()
        val jobOfCompanyA = createJob(companyId = companyAId)
        val jobOfCompanyB = createJob(companyId = companyBId)
        createApplication(jobOfCompanyA)
        createApplication(jobOfCompanyB)

        val result =
            jobApplicationAdminService.list(
                jobId = null,
                status = null,
                applicantName = null,
                cohort = null,
                department = null,
                companyId = companyAId,
                managerMemberId = null,
                mineOnly = false,
                requesterMemberId = 1L,
                pageable = PageRequest.of(0, 20),
            )

        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].jobId).isEqualTo(jobOfCompanyA)
    }

    @Test
    fun `managerMemberId Filter는 해당 교사가 담당하는 공고의 지원서만 반환한다`() {
        val managerId = 100L
        val otherTeacherId = 200L
        val managedJob = createJob(managerMemberId = managerId)
        val otherJob = createJob(managerMemberId = otherTeacherId)
        createApplication(managedJob)
        createApplication(otherJob)

        val result =
            jobApplicationAdminService.list(
                jobId = null,
                status = null,
                applicantName = null,
                cohort = null,
                department = null,
                companyId = null,
                managerMemberId = managerId,
                mineOnly = false,
                requesterMemberId = 1L,
                pageable = PageRequest.of(0, 20),
            )

        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].jobId).isEqualTo(managedJob)
    }

    // mineOnly는 managerMemberId 또는 createdByMemberId 중 하나만 일치해도 포함한다(사용자 확인
    // 완료, JobApplicationAdminFilterQueryPort KDoc 참고 -- 공고에 managerMemberId를 지정하는
    // API가 아직 없어 등록자 기준도 포함해야 "담당 공고" 필터가 항상 빈 목록이 되지 않는다).
    @Test
    fun `mineOnly는 담당(managerMemberId) 또는 등록(createdByMemberId) 공고를 모두 포함한다`() {
        val requesterId = 300L
        val managedJob = createJob(managerMemberId = requesterId, createdByMemberId = 999L)
        val createdJob = createJob(managerMemberId = null, createdByMemberId = requesterId)
        val unrelatedJob = createJob(managerMemberId = 111L, createdByMemberId = 222L)
        createApplication(managedJob)
        createApplication(createdJob)
        createApplication(unrelatedJob)

        val result =
            jobApplicationAdminService.list(
                jobId = null,
                status = null,
                applicantName = null,
                cohort = null,
                department = null,
                companyId = null,
                managerMemberId = null,
                mineOnly = true,
                requesterMemberId = requesterId,
                pageable = PageRequest.of(0, 20),
            )

        assertThat(result.content.map { it.jobId }).containsExactlyInAnyOrder(managedJob, createdJob)
    }

    private fun createCompany(): Long {
        val company =
            companyRepository.saveAndFlush(
                Company(name = "필터 Test 기업 ${UUID.randomUUID()}", type = CompanyType.GENERAL),
            )
        return requireNotNull(company.id)
    }

    private fun createJob(
        companyId: Long = createCompany(),
        managerMemberId: Long? = null,
        createdByMemberId: Long? = null,
    ): Long {
        val job =
            jobRepository.saveAndFlush(
                Job(
                    companyId = companyId,
                    type = PostingType.GENERAL,
                    applicationMethod = ApplicationMethod.INTERNAL,
                    title = "필터 Test 공고 ${UUID.randomUUID()}",
                    status = JobStatus.PUBLISHED,
                ).apply {
                    this.managerMemberId = managerMemberId
                    this.createdByMemberId = createdByMemberId
                },
            )
        return requireNotNull(job.id)
    }

    private fun createApplication(
        jobId: Long,
        applicantName: String? = "지원자 ${UUID.randomUUID()}",
        cohort: Int? = null,
        department: String? = null,
    ) {
        jobApplicationRepository.saveAndFlush(
            JobApplication(
                jobId = jobId,
                applicantMemberId = 1L,
                attemptNumber = 1,
                contactEmail = "applicant-${UUID.randomUUID()}@example.com",
                answers = "[]",
                status = JobApplicationStatus.SUBMITTED,
            ).apply {
                this.applicantName = applicantName
                this.applicantCohort = cohort
                this.applicantDepartment = department
            },
        )
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
