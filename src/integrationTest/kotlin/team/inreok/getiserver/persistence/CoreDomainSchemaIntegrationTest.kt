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
import team.inreok.getiserver.domain.ai.entity.type.AiDifficulty
import team.inreok.getiserver.domain.ai.entity.type.AiFitLevel
import team.inreok.getiserver.domain.ai.entity.type.AiStatus
import team.inreok.getiserver.domain.ai.repository.JobAiAnalysisRepository
import team.inreok.getiserver.domain.application.entity.JobApplication
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus
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
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
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
import team.inreok.getiserver.domain.notification.entity.type.NotificationType
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
        fun `Flyway로 생성한 Schema에는 정확히 42개의 비즈니스 Table이 있다`() {
            // persistence_probe는 integrationTest 전용 기술 검증 Table(V1__create_persistence_probe.sql)이며
            // GETI 비즈니스 Domain을 나타내지 않으므로 집계에서 제외한다. 최소 19개 Table ERD 기준
            // (docs/architecture/erd.md) 이후 Member 도메인 전공/기술 스택 정규화를 위해
            // majors, tech_stacks, member_majors, member_tech_stacks 4개 Table을 추가해 23개가 되었고,
            // Collector 도메인 실제 구현(Issue #62)을 위해 job_sources, collection_runs,
            // collection_run_errors 3개 Table을 추가해 26개가 되었으며, Issue #62 확장 범위(Discord
            // 신규 공고 알림)를 위해 job_notification_deliveries 1개 Table을 추가해 27개가 되었다.
            // Search 도메인(Issue #69)의 색인 실패 재처리·재색인 실행 이력을 위해 search_index_failures,
            // search_reindex_runs 2개 Table을 추가해 29개가 되었고, Application 도메인 개인 신청
            // 양식(Epic #75, Issue #76)을 위해 forms, form_versions 2개 Table을 추가해 31개가 되었으며,
            // Application Phase 2(Issue #78) 공고-양식 연결을 위해 job_application_forms 1개
            // Table을 추가해 32개가 되었다. Program 도메인 Phase 1(등록·수정·상태 관리)을 위해
            // program_target_grades 1개 Table을 추가해 33개가 되었다(V14 Migration). Inquiry 도메인이
            // 1:N 답변 구조로 재구성되며(GETI Inquiry 도메인 개발 요구사항 20절, 사용자 확인 완료)
            // inquiry_answers 1개 Table을 추가해 34개가 되었다(V18 Migration). Notification 도메인이
            // Discord 전달 상태의 Source of Truth가 되면서 discord_deliveries,
            // discord_delivery_attempts 2개 Table을 추가해 36개가 되었다(V19 Migration,
            // docs/notification/discord-delivery-plan.md). Application Phase 5(Epic #75,
            // Issue #133) 상태 이력·제출 Snapshot을 위해 job_application_status_histories,
            // job_application_submissions 2개 Table을 추가해 38개가 되었다(V20 Migration).
            // Recommendation R3(Issue #152) 추천 기능 ON/OFF 설정을 위해 recommendation_preferences
            // 1개 Table을 추가해 39개가 되었다(V23 Migration). Recommendation R4(Issue #160) 일일
            // 추천 Generation 상태 저장을 위해 recommendation_generation_states 1개 Table을
            // 추가해 40개가 되었다(V25 Migration). Portfolio Core Phase 1(수합 요청)에서 제출 대상
            // 학생을 저장하는 portfolio_request_targets 1개 Table과 프로필 링크를 저장하는
            // member_profile_links 1개 Table을 추가해 42개가 되었다(V27, V31 Migration).
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

            assertThat(tableCount.toInt()).isEqualTo(42)
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
                    },
                )

            val found = memberRepository.findById(member.id!!).orElseThrow()
            assertThat(found.department).isEqualTo(DepartmentType.SW_DEVELOPMENT)
            assertThat(found.academicStatus).isEqualTo(AcademicStatus.WITHDRAWN)
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
                        purpose = FilePurpose.PORTFOLIO,
                        objectKey = "PORTFOLIO/2026/08/portfolio-file",
                        originalName = "file.pdf",
                        contentType = "application/pdf",
                        sizeBytes = 1024L,
                        uploaderMemberId = member.id,
                    ).apply {
                        // V17의 ck_files_link_state가 "연결된 파일만 owner_*를 가진다"를 강제하므로
                        // 업로드 완료 -> 연결 순서를 그대로 거친다.
                        markUploaded()
                        linkTo(FileOwnerType.PORTFOLIO_SUBMISSION, 1L, LocalDateTime.now())
                    },
                )

            memberRepository.delete(member)
            memberRepository.flush()
            // SET NULL은 PostgreSQL이 DB 차원에서 수행하므로 Hibernate 1차 Cache에는 반영되지
            // 않는다. 다시 조회하기 전에 영속성 Context를 비워 실제 DB 상태를 확인한다.
            entityManager.clear()

            val found = fileRepository.findById(file.id!!).orElseThrow()
            assertThat(found.uploaderMemberId).isNull()
            assertThat(found.ownerType).isEqualTo(FileOwnerType.PORTFOLIO_SUBMISSION)
        }

        @Test
        fun `size_bytes는 음수로 저장할 수 없다`() {
            assertThatThrownBy {
                fileRepository.saveAndFlush(
                    StoredFile(
                        purpose = FilePurpose.JOB_ATTACHMENT,
                        objectKey = "JOB_ATTACHMENT/2026/08/invalid",
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
        fun `member_job_preferences는 Surrogate id를 PK로 쓰고 member_id와 job_id 조합은 UNIQUE이며 exclusion은 NULL을 허용한다`() {
            val member = persistMember("preference-subject")
            val company = persistCompany()
            val job = jobRepository.saveAndFlush(newJob(company.id!!))

            val preference =
                memberJobPreferenceRepository.saveAndFlush(
                    MemberJobPreference(memberId = member.id!!, jobId = job.id!!, bookmarked = true),
                )

            assertThat(preference.id).isNotNull
            assertThat(preference.exclusion).isNull()

            preference.exclusion = ExclusionType.SIMILAR_JOBS
            memberJobPreferenceRepository.saveAndFlush(preference)

            val found = memberJobPreferenceRepository.findById(preference.id!!)
            assertThat(found).isPresent
            assertThat(found.get().exclusion).isEqualTo(ExclusionType.SIMILAR_JOBS)

            // (member_id, job_id) 조합의 유일성은 이제 PK가 아니라
            // uk_member_job_preferences_member_job UNIQUE 제약이 보존한다(V24 Migration).
            assertThatThrownBy {
                memberJobPreferenceRepository.saveAndFlush(
                    MemberJobPreference(memberId = member.id!!, jobId = job.id!!),
                )
            }.isInstanceOf(DataIntegrityViolationException::class.java)
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
        fun `job_ai_analyses는 V21이 추가한 구조화 결과 Column을 저장·조회한다`() {
            val company = persistCompany()
            val job = jobRepository.saveAndFlush(newJob(company.id!!))

            val saved =
                jobAiAnalysisRepository.saveAndFlush(
                    JobAiAnalysis(
                        jobId = job.id!!,
                        status = AiStatus.COMPLETED,
                        requestedAt = LocalDateTime.now(),
                    ).apply {
                        summary = "요약"
                        requiredSkills = """[{"techStackId":1,"name":"Spring Boot"}]"""
                        preferredSkills = """[{"techStackId":null,"name":"Docker"}]"""
                        highSchoolGraduateFit = AiFitLevel.SUITABLE
                        entryLevelFit = AiFitLevel.SUITABLE
                        difficulty = AiDifficulty.NORMAL
                        provider = "OPENAI"
                        model = "gpt-4o-mini"
                        promptVersion = "JOB_ANALYSIS_V1"
                        analysisVersion = 1
                        completedAt = LocalDateTime.now()
                    },
                )
            entityManager.flush()
            entityManager.clear()

            val found = jobAiAnalysisRepository.findById(saved.jobId).orElseThrow()
            assertThat(found.requiredSkills).contains("Spring Boot")
            assertThat(found.highSchoolGraduateFit).isEqualTo(AiFitLevel.SUITABLE)
            assertThat(found.difficulty).isEqualTo(AiDifficulty.NORMAL)
            assertThat(found.provider).isEqualTo("OPENAI")
            assertThat(found.analysisVersion).isEqualTo(1)
        }

        @Test
        fun `job_ai_analyses의 difficulty는 잘못된 값을 CHECK 제약으로 거부한다`() {
            val company = persistCompany()
            val job = jobRepository.saveAndFlush(newJob(company.id!!))

            assertThatThrownBy {
                entityManager
                    .createNativeQuery(
                        "INSERT INTO job_ai_analyses (job_id, status, reanalysis_count, requested_at, difficulty) " +
                            "VALUES (:jobId, 'PENDING', 0, now(), 'IMPOSSIBLE')",
                    ).setParameter("jobId", job.id!!)
                    .executeUpdate()
            }.isInstanceOf(Exception::class.java)
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
                    rank = 1,
                    algorithmVersion = 1,
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
                        rank = 1,
                        algorithmVersion = 1,
                    ),
                )
            }.isInstanceOf(DataIntegrityViolationException::class.java)
        }

        @Test
        fun `job_applications는 job_id, applicant_member_id, attempt_number 조합이 유일해야 한다`() {
            val member = persistMember("apply-subject")
            val company = persistCompany()
            val job = jobRepository.saveAndFlush(newJob(company.id!!))

            // uk_job_applications_active_singleton(V13 Migration, PR #79 Review 반영)이 같은
            // (job_id, applicant_member_id) 조합에 활성 Row를 최대 1건으로 강제하므로, 재지원
            // 시나리오와 동일하게 이전 attempt를 WITHDRAWN으로 만든 뒤 다음 attempt를 저장한다.
            jobApplicationRepository.saveAndFlush(
                newJobApplication(job.id!!, member.id!!, 1).apply { status = JobApplicationStatus.WITHDRAWN },
            )
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
                        dueAt = LocalDateTime.now().plusDays(7),
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
                    // 서버가 생성하는 Export 결과물에는 아직 전용 FilePurpose가 없다(일괄 다운로드,
                    // Phase 6 범위). 이 Test는 async_operations.result_file_id FK 경로만 확인하므로
                    // 유효한 Purpose 하나를 골라 쓰고 연결은 하지 않는다.
                    StoredFile(
                        purpose = FilePurpose.JOB_APPLICATION,
                        objectKey = "JOB_APPLICATION/2026/08/export-result",
                        originalName = "result.csv",
                        contentType = "text/csv",
                        sizeBytes = 2048L,
                    ),
                )

            val notification =
                notificationRepository.saveAndFlush(
                    Notification(
                        recipientMemberId = member.id!!,
                        type = NotificationType.JOB_APPLICATION_STATUS_CHANGED,
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

        @Test
        fun `job_applications의 attempt_number는 1 이상이어야 한다`() {
            val member = persistMember("attempt-subject")
            val company = persistCompany()
            val job = jobRepository.saveAndFlush(newJob(company.id!!))

            assertThatThrownBy {
                jobApplicationRepository.saveAndFlush(newJobApplication(job.id!!, member.id!!, 0))
            }.isInstanceOf(DataIntegrityViolationException::class.java)
        }

        @Test
        fun `다형적 참조 Column(files, notifications, async_operations, audit_logs)에는 물리 FK가 없다`() {
            // files.owner_id, notifications.target_id, async_operations.target_id, audit_logs.target_id는
            // owner_type/target_type과 함께 쓰이는 논리적 참조이며, 특정 Table을 가리키는
            // 물리 FK 제약을 만들지 않는다(docs/architecture/erd.md의 "다형적 참조" 참고).
            // notifications는 V16에서 resource_type/resource_id -> target_type/target_id로 바뀌었다.
            @Suppress("UNCHECKED_CAST")
            val polymorphicForeignKeyCount =
                entityManager
                    .createNativeQuery(
                        """
                        SELECT count(*)
                        FROM information_schema.key_column_usage kcu
                        JOIN information_schema.table_constraints tc
                          ON tc.constraint_name = kcu.constraint_name
                         AND tc.table_schema = kcu.table_schema
                        WHERE tc.constraint_type = 'FOREIGN KEY'
                          AND (
                            (kcu.table_name = 'files' AND kcu.column_name = 'owner_id')
                            OR (kcu.table_name = 'notifications' AND kcu.column_name = 'target_id')
                            OR (kcu.table_name = 'async_operations' AND kcu.column_name = 'target_id')
                            OR (kcu.table_name = 'audit_logs' AND kcu.column_name = 'target_id')
                          )
                        """.trimIndent(),
                    ).singleResult as Number

            assertThat(polymorphicForeignKeyCount.toInt()).isEqualTo(0)
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
