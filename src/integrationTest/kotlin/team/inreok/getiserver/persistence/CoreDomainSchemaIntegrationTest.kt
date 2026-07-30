package team.inreok.getiserver.persistence

import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
import team.inreok.getiserver.domain.ai.entity.JobAiAnalysis
import team.inreok.getiserver.domain.ai.repository.JobAiAnalysisRepository
import team.inreok.getiserver.domain.application.entity.JobApplication
import team.inreok.getiserver.domain.application.repository.JobApplicationRepository
import team.inreok.getiserver.domain.audit.entity.AuditLog
import team.inreok.getiserver.domain.audit.repository.AuditLogRepository
import team.inreok.getiserver.domain.auth.entity.RefreshToken
import team.inreok.getiserver.domain.auth.repository.RefreshTokenRepository
import team.inreok.getiserver.domain.collector.entity.JobCollectionRun
import team.inreok.getiserver.domain.collector.repository.JobCollectionRunRepository
import team.inreok.getiserver.domain.company.entity.Company
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.company.entity.type.MouStatus
import team.inreok.getiserver.domain.company.repository.CompanyRepository
import team.inreok.getiserver.domain.file.entity.StoredFile
import team.inreok.getiserver.domain.file.repository.StoredFileRepository
import team.inreok.getiserver.domain.inquiry.entity.Inquiry
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryType
import team.inreok.getiserver.domain.inquiry.repository.InquiryRepository
import team.inreok.getiserver.domain.job.entity.Job
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.PostingType
import team.inreok.getiserver.domain.job.repository.JobRepository
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.MemberRole
import team.inreok.getiserver.domain.member.entity.MemberRoleId
import team.inreok.getiserver.domain.member.entity.type.AcademicStatus
import team.inreok.getiserver.domain.member.entity.type.DepartmentType
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.member.repository.MemberRoleRepository
import team.inreok.getiserver.domain.notification.entity.Notification
import team.inreok.getiserver.domain.notification.repository.NotificationRepository
import team.inreok.getiserver.domain.operation.entity.AsyncOperation
import team.inreok.getiserver.domain.operation.entity.type.OperationType
import team.inreok.getiserver.domain.operation.repository.AsyncOperationRepository
import team.inreok.getiserver.domain.portfolio.entity.PortfolioRequest
import team.inreok.getiserver.domain.portfolio.entity.PortfolioSubmission
import team.inreok.getiserver.domain.portfolio.repository.PortfolioRequestRepository
import team.inreok.getiserver.domain.portfolio.repository.PortfolioSubmissionRepository
import team.inreok.getiserver.domain.program.entity.Program
import team.inreok.getiserver.domain.program.entity.ProgramApplication
import team.inreok.getiserver.domain.program.entity.type.ProgramType
import team.inreok.getiserver.domain.program.repository.ProgramApplicationRepository
import team.inreok.getiserver.domain.program.repository.ProgramRepository
import team.inreok.getiserver.domain.recommendation.entity.MemberJobPreference
import team.inreok.getiserver.domain.recommendation.entity.MemberJobPreferenceId
import team.inreok.getiserver.domain.recommendation.entity.Recommendation
import team.inreok.getiserver.domain.recommendation.entity.type.ExclusionType
import team.inreok.getiserver.domain.recommendation.entity.type.SuitabilityLevel
import team.inreok.getiserver.domain.recommendation.repository.MemberJobPreferenceRepository
import team.inreok.getiserver.domain.recommendation.repository.RecommendationRepository
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 최신 19개 Table 최소 ERD가 실제 Flyway Migration(V2__create_core_domain_schema.sql)과
 * JPA Entity Mapping(ddl-auto=validate) 양쪽에서 일관되는지 PostgreSQL Testcontainers로 검증한다.
 * 실제 Domain Service/Controller 동작은 검증하지 않고 Persistence 경로만 다룬다.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration::class)
class CoreDomainSchemaIntegrationTest
    @Autowired
    constructor(
        private val entityManager: EntityManager,
        private val memberRepository: MemberRepository,
        private val memberRoleRepository: MemberRoleRepository,
        private val refreshTokenRepository: RefreshTokenRepository,
        private val fileRepository: StoredFileRepository,
        private val companyRepository: CompanyRepository,
        private val jobRepository: JobRepository,
        private val memberJobPreferenceRepository: MemberJobPreferenceRepository,
        private val jobAiAnalysisRepository: JobAiAnalysisRepository,
        private val recommendationRepository: RecommendationRepository,
        private val jobApplicationRepository: JobApplicationRepository,
        private val programRepository: ProgramRepository,
        private val programApplicationRepository: ProgramApplicationRepository,
        private val portfolioRequestRepository: PortfolioRequestRepository,
        private val portfolioSubmissionRepository: PortfolioSubmissionRepository,
        private val notificationRepository: NotificationRepository,
        private val inquiryRepository: InquiryRepository,
        private val jobCollectionRunRepository: JobCollectionRunRepository,
        private val asyncOperationRepository: AsyncOperationRepository,
        private val auditLogRepository: AuditLogRepository,
    ) {
        @Test
        fun `Flyway로 생성한 Schema에는 정확히 19개의 비즈니스 Table이 있다`() {
            // persistence_probe는 integrationTest 전용 기술 검증 Table(V1__create_persistence_probe.sql)이며
            // GETI 비즈니스 Domain을 나타내지 않으므로 19개 집계에서 제외한다.
            @Suppress("UNCHECKED_CAST")
            val tableCount =
                entityManager
                    .createNativeQuery(
                        """
                        SELECT count(*) FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_type = 'BASE TABLE'
                          AND table_name NOT IN ('flyway_schema_history', 'persistence_probe')
                        """.trimIndent(),
                    ).singleResult as Number

            assertThat(tableCount.toInt()).isEqualTo(19)
        }

        @Test
        fun `이름이 NULL인 회원도 저장하고 oauth_provider와 oauth_subject로 조회할 수 있다`() {
            val member =
                memberRepository.saveAndFlush(
                    Member(
                        oauthProvider = OAuthProvider.DG,
                        oauthSubject = "dg-user-1",
                        email = "student1@example.com",
                    ),
                )

            assertThat(member.id).isNotNull
            assertThat(member.name).isNull()

            val found = memberRepository.findByOauthProviderAndOauthSubject(OAuthProvider.DG, "dg-user-1")
            assertThat(found).isNotNull
            assertThat(found!!.email).isEqualTo("student1@example.com")
        }

        @Test
        fun `동일한 oauth_provider와 oauth_subject 조합은 중복 저장할 수 없다`() {
            memberRepository.saveAndFlush(
                Member(oauthProvider = OAuthProvider.DG, oauthSubject = "dup-subject", email = "dup1@example.com"),
            )

            assertThatThrownBy {
                memberRepository.saveAndFlush(
                    Member(oauthProvider = OAuthProvider.DG, oauthSubject = "dup-subject", email = "dup2@example.com"),
                )
            }.isInstanceOf(DataIntegrityViolationException::class.java)
        }

        @Test
        fun `SW_DEVELOPMENT 학과와 학적상 WITHDRAWN 상태를 저장하고 조회할 수 있다`() {
            val member =
                memberRepository.saveAndFlush(
                    Member(oauthProvider = OAuthProvider.DG, oauthSubject = "sw-1", email = "sw1@example.com").apply {
                        department = DepartmentType.SW_DEVELOPMENT
                        academicStatus = AcademicStatus.WITHDRAWN
                        majors = """["backend","ai"]"""
                    },
                )

            val found = memberRepository.findById(member.id!!).orElseThrow()
            assertThat(found.department).isEqualTo(DepartmentType.SW_DEVELOPMENT)
            assertThat(found.academicStatus).isEqualTo(AcademicStatus.WITHDRAWN)
            assertThat(found.majors).contains("backend")
        }

        @Test
        fun `member_roles는 member_id와 role 복합키로 저장하고 조회할 수 있다`() {
            val member = persistMember("role-subject")

            val saved = memberRoleRepository.saveAndFlush(MemberRole(MemberRoleId(member.id!!, RoleType.STUDENT)))

            val found = memberRoleRepository.findById(MemberRoleId(member.id!!, RoleType.STUDENT))
            assertThat(found).isPresent
            assertThat(found.get().grantedAt).isNotNull
            assertThat(saved.id.role).isEqualTo(RoleType.STUDENT)
        }

        @Test
        fun `refresh_tokens의 token_hash는 유일해야 한다`() {
            val member = persistMember("refresh-subject")

            refreshTokenRepository.saveAndFlush(
                RefreshToken(
                    memberId = member.id!!,
                    tokenHash = "hash-1",
                    expiresAt = LocalDateTime.now().plusDays(30),
                ),
            )

            assertThat(refreshTokenRepository.findByTokenHash("hash-1")).isNotNull

            // 중복 저장은 예외를 던지므로 Transaction을 계속 사용하는 검증은 이보다 먼저 수행한다.
            assertThatThrownBy {
                refreshTokenRepository.saveAndFlush(
                    RefreshToken(
                        memberId = member.id!!,
                        tokenHash = "hash-1",
                        expiresAt = LocalDateTime.now().plusDays(30),
                    ),
                )
            }.isInstanceOf(DataIntegrityViolationException::class.java)
        }

        @Test
        fun `member가 삭제되면 member_roles와 refresh_tokens는 함께 삭제된다`() {
            val member = persistMember("cascade-subject")
            memberRoleRepository.saveAndFlush(MemberRole(MemberRoleId(member.id!!, RoleType.STUDENT)))
            refreshTokenRepository.saveAndFlush(
                RefreshToken(
                    memberId = member.id!!,
                    tokenHash = "cascade-hash",
                    expiresAt = LocalDateTime.now().plusDays(1),
                ),
            )

            memberRepository.delete(member)
            memberRepository.flush()
            // ON DELETE CASCADE는 PostgreSQL이 DB 차원에서 수행하므로 Hibernate 1차 Cache에는
            // 반영되지 않는다. 다시 조회하기 전에 영속성 Context를 비워 실제 DB 상태를 확인한다.
            entityManager.clear()

            assertThat(memberRoleRepository.findById(MemberRoleId(member.id!!, RoleType.STUDENT))).isEmpty
            assertThat(refreshTokenRepository.findByTokenHash("cascade-hash")).isNull()
        }

        @Test
        fun `files는 owner_type과 owner_id로 파일을 소유하고 uploader_member_id는 SET NULL 정책을 따른다`() {
            val member = persistMember("uploader-subject")
            val file =
                fileRepository.saveAndFlush(
                    StoredFile(
                        ownerType = "PORTFOLIO_SUBMISSION",
                        ownerId = 1L,
                        objectKey = "portfolio/1/file.pdf",
                        originalName = "file.pdf",
                        contentType = "application/pdf",
                        sizeBytes = 1024L,
                    ).apply { uploaderMemberId = member.id },
                )

            memberRepository.delete(member)
            memberRepository.flush()
            // SET NULL은 PostgreSQL이 DB 차원에서 수행하므로 Hibernate 1차 Cache에는 반영되지
            // 않는다. 다시 조회하기 전에 영속성 Context를 비워 실제 DB 상태를 확인한다.
            entityManager.clear()

            val found = fileRepository.findById(file.id!!).orElseThrow()
            assertThat(found.uploaderMemberId).isNull()
            assertThat(found.ownerType).isEqualTo("PORTFOLIO_SUBMISSION")
        }

        @Test
        fun `size_bytes는 음수로 저장할 수 없다`() {
            assertThatThrownBy {
                fileRepository.saveAndFlush(
                    StoredFile(
                        ownerType = "JOB",
                        ownerId = 1L,
                        objectKey = "invalid.pdf",
                        originalName = "invalid.pdf",
                        contentType = "application/pdf",
                        sizeBytes = -1L,
                    ),
                )
            }.isInstanceOf(DataIntegrityViolationException::class.java)
        }

        @Test
        fun `외부 수집 공고는 source_name과 external_job_id 조합이 유일하지만 내부 공고는 둘 다 NULL이어도 여러 건 저장할 수 있다`() {
            val company = persistCompany()

            jobRepository.saveAndFlush(
                newJob(company.id!!).apply {
                    sourceName = "saramin"
                    externalJobId = "ext-1"
                },
            )

            // 내부 공고(source_name/external_job_id 둘 다 NULL)는 여러 건 저장할 수 있다.
            jobRepository.saveAndFlush(newJob(company.id!!))
            jobRepository.saveAndFlush(newJob(company.id!!))

            val found = jobRepository.findBySourceNameAndExternalJobId("saramin", "ext-1")
            assertThat(found).isNotNull

            // 중복 저장은 예외를 던져 Transaction을 계속 쓸 수 없게 만들므로 마지막에 검증한다.
            assertThatThrownBy {
                jobRepository.saveAndFlush(
                    newJob(company.id!!).apply {
                        sourceName = "saramin"
                        externalJobId = "ext-1"
                    },
                )
            }.isInstanceOf(DataIntegrityViolationException::class.java)
        }

        @Test
        fun `member_job_preferences는 member_id와 job_id 복합키를 사용하고 exclusion은 NULL을 허용한다`() {
            val member = persistMember("preference-subject")
            val company = persistCompany()
            val job = jobRepository.saveAndFlush(newJob(company.id!!))

            val preference =
                memberJobPreferenceRepository.saveAndFlush(
                    MemberJobPreference(MemberJobPreferenceId(member.id!!, job.id!!), bookmarked = true),
                )

            assertThat(preference.exclusion).isNull()

            preference.exclusion = ExclusionType.SIMILAR_JOBS
            memberJobPreferenceRepository.saveAndFlush(preference)

            val found = memberJobPreferenceRepository.findById(MemberJobPreferenceId(member.id!!, job.id!!))
            assertThat(found).isPresent
            assertThat(found.get().exclusion).isEqualTo(ExclusionType.SIMILAR_JOBS)
        }

        @Test
        fun `job_ai_analyses는 job_id를 공유 PK로 사용하고 reanalysis_count는 0에서 3 사이여야 한다`() {
            val company = persistCompany()
            val job = jobRepository.saveAndFlush(newJob(company.id!!))

            val analysis =
                jobAiAnalysisRepository.saveAndFlush(
                    JobAiAnalysis(jobId = job.id!!, requestedAt = LocalDateTime.now()).apply {
                        reanalysisCount = 3
                    },
                )
            assertThat(analysis.jobId).isEqualTo(job.id)

            assertThatThrownBy {
                jobAiAnalysisRepository.saveAndFlush(
                    JobAiAnalysis(
                        jobId = jobRepository.saveAndFlush(newJob(company.id!!)).id!!,
                        requestedAt = LocalDateTime.now(),
                    ).apply { reanalysisCount = 4 },
                )
            }.isInstanceOf(DataIntegrityViolationException::class.java)
        }

        @Test
        fun `recommendations는 member_id, job_id, recommendation_date 조합이 유일해야 한다`() {
            val member = persistMember("recommend-subject")
            val company = persistCompany()
            val job = jobRepository.saveAndFlush(newJob(company.id!!))
            val today = LocalDate.now()

            recommendationRepository.saveAndFlush(
                Recommendation(
                    memberId = member.id!!,
                    jobId = job.id!!,
                    recommendationDate = today,
                    score = BigDecimal("87.5000"),
                    suitability = SuitabilityLevel.RECOMMENDED,
                ),
            )

            assertThatThrownBy {
                recommendationRepository.saveAndFlush(
                    Recommendation(
                        memberId = member.id!!,
                        jobId = job.id!!,
                        recommendationDate = today,
                        score = BigDecimal("10.0000"),
                        suitability = SuitabilityLevel.UNSUITABLE,
                    ),
                )
            }.isInstanceOf(DataIntegrityViolationException::class.java)
        }

        @Test
        fun `job_applications는 job_id, applicant_member_id, attempt_number 조합이 유일해야 한다`() {
            val member = persistMember("apply-subject")
            val company = persistCompany()
            val job = jobRepository.saveAndFlush(newJob(company.id!!))

            jobApplicationRepository.saveAndFlush(newJobApplication(job.id!!, member.id!!, 1))
            jobApplicationRepository.saveAndFlush(newJobApplication(job.id!!, member.id!!, 2))
            val found =
                jobApplicationRepository.findByJobIdAndApplicantMemberIdAndAttemptNumber(
                    job.id!!,
                    member.id!!,
                    2,
                )
            assertThat(found).isNotNull

            // 중복 저장은 예외를 던져 Transaction을 계속 쓸 수 없게 만들므로 마지막에 검증한다.
            assertThatThrownBy {
                jobApplicationRepository.saveAndFlush(newJobApplication(job.id!!, member.id!!, 1))
            }.isInstanceOf(DataIntegrityViolationException::class.java)
        }

        @Test
        fun `programs와 program_applications를 저장하고 조회할 수 있다`() {
            val member = persistMember("program-subject")
            val program =
                programRepository.saveAndFlush(
                    Program(
                        createdByMemberId = member.id!!,
                        type = ProgramType.SPECIAL_LECTURE,
                        title = "이력서 작성 특강",
                    ),
                )

            val application =
                programApplicationRepository.saveAndFlush(
                    ProgramApplication(programId = program.id!!, applicantMemberId = member.id!!),
                )

            assertThat(application.appliedAt).isNotNull
            assertThat(programApplicationRepository.findById(application.id!!)).isPresent
        }

        @Test
        fun `portfolio_submissions는 request_id와 member_id 조합이 유일해야 한다`() {
            val member = persistMember("portfolio-subject")
            val request =
                portfolioRequestRepository.saveAndFlush(
                    PortfolioRequest(
                        createdByMemberId = member.id!!,
                        title = "3학년 포트폴리오 제출",
                        targetCondition = """{"grade":3}""",
                        submissionStartedAt = LocalDateTime.now(),
                        submissionEndedAt = LocalDateTime.now().plusDays(7),
                    ),
                )

            portfolioSubmissionRepository.saveAndFlush(
                PortfolioSubmission(requestId = request.id!!, memberId = member.id!!),
            )

            assertThatThrownBy {
                portfolioSubmissionRepository.saveAndFlush(
                    PortfolioSubmission(requestId = request.id!!, memberId = member.id!!),
                )
            }.isInstanceOf(DataIntegrityViolationException::class.java)
        }

        @Test
        fun `notifications, inquiries, job_collection_runs, async_operations, audit_logs 전체 영속성 경로를 저장하고 조회할 수 있다`() {
            val member = persistMember("misc-subject")
            val file =
                fileRepository.saveAndFlush(
                    StoredFile(
                        ownerType = "ASYNC_OPERATION_RESULT",
                        ownerId = 1L,
                        objectKey = "export/1/result.csv",
                        originalName = "result.csv",
                        contentType = "text/csv",
                        sizeBytes = 2048L,
                    ),
                )

            val notification =
                notificationRepository.saveAndFlush(
                    Notification(
                        recipientMemberId = member.id!!,
                        type = "JOB_APPLICATION_APPROVED",
                        title = "지원이 승인되었습니다",
                        content = "지원하신 공고가 승인되었습니다.",
                    ),
                )
            assertThat(notification.isRead).isFalse

            val inquiry =
                inquiryRepository.saveAndFlush(
                    Inquiry(
                        authorMemberId = member.id!!,
                        type = InquiryType.FEATURE_REQUEST,
                        title = "다크 모드 지원 요청",
                        content = "다크 모드를 지원해 주세요.",
                    ),
                )
            assertThat(inquiryRepository.findById(inquiry.id!!)).isPresent

            val collectionRun =
                jobCollectionRunRepository.saveAndFlush(
                    JobCollectionRun(sourceName = "saramin", startedAt = LocalDateTime.now()),
                )
            assertThat(jobCollectionRunRepository.findById(collectionRun.id!!)).isPresent

            val asyncOperation =
                asyncOperationRepository.saveAndFlush(
                    AsyncOperation(type = OperationType.PORTFOLIO_EXPORT).apply {
                        requestedByMemberId = member.id
                        resultFileId = file.id
                    },
                )
            assertThat(asyncOperation.id).isNotNull
            assertThat(asyncOperationRepository.findById(asyncOperation.id!!)).isPresent

            val auditLog =
                auditLogRepository.saveAndFlush(
                    AuditLog(action = "MEMBER_APPROVED", targetType = "MEMBER").apply {
                        actorMemberId = member.id
                        targetId = member.id
                    },
                )
            assertThat(auditLogRepository.findById(auditLog.id!!)).isPresent
        }

        @Test
        fun `async_operations의 progress_percent는 0에서 100 사이여야 한다`() {
            assertThatThrownBy {
                asyncOperationRepository.saveAndFlush(
                    AsyncOperation(type = OperationType.SEARCH_REINDEX).apply { progressPercent = 101 },
                )
            }.isInstanceOf(DataIntegrityViolationException::class.java)
        }

        private fun persistMember(subject: String): Member =
            memberRepository.saveAndFlush(
                Member(oauthProvider = OAuthProvider.DG, oauthSubject = subject, email = "$subject@example.com"),
            )

        private fun persistCompany(): Company =
            companyRepository.saveAndFlush(
                Company(name = "GETI 테스트 기업", type = CompanyType.GENERAL, mouStatus = MouStatus.ACTIVE),
            )

        private fun newJob(companyId: Long): Job =
            Job(
                companyId = companyId,
                type = PostingType.GENERAL,
                applicationMethod = ApplicationMethod.EXTERNAL,
                title = "백엔드 개발자 채용",
            )

        private fun newJobApplication(
            jobId: Long,
            memberId: Long,
            attemptNumber: Int,
        ): JobApplication =
            JobApplication(
                jobId = jobId,
                applicantMemberId = memberId,
                attemptNumber = attemptNumber,
                contactEmail = "applicant@example.com",
                answers = "{}",
            )

        companion object {
            @Container
            @ServiceConnection
            @JvmStatic
            val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18.4-alpine"))
        }
    }
