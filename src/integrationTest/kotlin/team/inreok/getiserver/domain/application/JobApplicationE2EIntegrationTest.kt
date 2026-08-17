package team.inreok.getiserver.domain.application

import com.redis.testcontainers.RedisContainer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.data.domain.PageRequest
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import team.inreok.getiserver.domain.application.dto.ApplicationAnswer
import team.inreok.getiserver.domain.application.dto.CreateFormRequest
import team.inreok.getiserver.domain.application.dto.CreateJobApplicationRequest
import team.inreok.getiserver.domain.application.dto.FormFieldRequest
import team.inreok.getiserver.domain.application.dto.JobApplicationAction
import team.inreok.getiserver.domain.application.dto.JobApplicationActionRequest
import team.inreok.getiserver.domain.application.dto.JobApplicationAdminAction
import team.inreok.getiserver.domain.application.dto.JobApplicationAdminActionRequest
import team.inreok.getiserver.domain.application.dto.JobApplicationFormLinkRequest
import team.inreok.getiserver.domain.application.dto.SaveJobApplicationDraftRequest
import team.inreok.getiserver.domain.application.entity.type.FormFieldType
import team.inreok.getiserver.domain.application.entity.type.FormStatus
import team.inreok.getiserver.domain.application.entity.type.FormType
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus
import team.inreok.getiserver.domain.application.exception.ApplicationAccessForbiddenException
import team.inreok.getiserver.domain.application.exception.ApplicationReviewForbiddenException
import team.inreok.getiserver.domain.application.service.FormService
import team.inreok.getiserver.domain.application.service.JobApplicationAdminService
import team.inreok.getiserver.domain.application.service.JobApplicationFormLinkService
import team.inreok.getiserver.domain.application.service.JobApplicationService
import team.inreok.getiserver.domain.company.entity.Company
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.company.repository.CompanyRepository
import team.inreok.getiserver.domain.file.entity.StoredFile
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.repository.StoredFileRepository
import team.inreok.getiserver.domain.job.entity.Job
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.entity.type.PostingType
import team.inreok.getiserver.domain.job.repository.JobRepository
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.type.AcademicStatus
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.notification.entity.Notification
import team.inreok.getiserver.domain.notification.repository.NotificationRepository
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

/**
 * Application Phase 11(Issue #139)의 "필수 E2E 시나리오"다. Phase 1~10이 만든 개별 Service를
 * 직접(가짜 대체 없이 실제 Spring Bean으로) 이어 붙여, 각 Phase Test(Mock 기반 Service Test)가
 * 검증하지 못하는 여러 Service·Transaction·실제 PostgreSQL을 가로지르는 전체 Flow를 확인한다.
 *
 * HTTP Layer(Controller, SecurityConfig의 Role 요구사항)는 이 Test의 관심사가 아니다 -- Role
 * 기반 인가는 각 Controller의 `@WebMvcTest`가 이미 검증한다(예: `JobApplicationAdminControllerTest`).
 * 여기서는 Service 경계를 넘나드는 상태 전이·소유권·Snapshot 일관성만 실제 DB로 확인한다.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "app.jwt.secret=job-application-e2e-integration-test-only-jwt-secret-value",
        "app.jwt.access-token-expiration-seconds=1800",
        "app.jwt.refresh-token-expiration-seconds=1209600",
        "app.file.storage.bucket=geti-integration-test",
        "app.file.storage.region=us-east-1",
        "app.file.storage.access-key=integration-test-only-access-key",
        "app.file.storage.secret-key=integration-test-only-secret-key",
    ],
)
class JobApplicationE2EIntegrationTest {
    @Autowired
    private lateinit var formService: FormService

    @Autowired
    private lateinit var jobApplicationFormLinkService: JobApplicationFormLinkService

    @Autowired
    private lateinit var jobApplicationService: JobApplicationService

    @Autowired
    private lateinit var jobApplicationAdminService: JobApplicationAdminService

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var companyRepository: CompanyRepository

    @Autowired
    private lateinit var jobRepository: JobRepository

    @Autowired
    private lateinit var storedFileRepository: StoredFileRepository

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    private val objectMapper = JsonMapper()

    @Test
    fun `정상 지원 Flow -- Form 생성부터 승인 Notification까지 이어진다`() {
        val teacherId = createMember("normal-flow-teacher")
        val studentId = createStudent("normal-flow-student")
        val jobId = createJob(teacherId, "normal-flow")

        // 교사 Form 생성(ACTIVE로 바로 생성 -- FormServiceImpl.create는 ARCHIVED만 막는다)
        val motivationField =
            FormFieldRequest(
                key = "motivation",
                type = FormFieldType.TEXTAREA,
                label = "지원 동기",
                required = true,
            )
        val resumeField =
            FormFieldRequest(
                key = "resume",
                type = FormFieldType.FILE,
                label = "이력서",
                required = false,
                // FILE 유형은 filePolicy가 없으면 InvalidFormFieldException이다
                // (FormFieldValidation.kt). 내부 속성이 아직 확정되지 않아 원문 JSON을 그대로
                // 저장·반환하므로 이 Test에서는 빈 객체로 충분하다.
                filePolicy = objectMapper.readTree("{}"),
            )
        val form =
            formService.create(
                teacherId,
                CreateFormRequest(
                    name = "정상 Flow 지원서",
                    formType = FormType.JOB,
                    status = FormStatus.ACTIVE,
                    fields = listOf(motivationField, resumeField),
                ),
            )

        // Job에 Form 연결
        val linkRequest = JobApplicationFormLinkRequest(form.formId)
        jobApplicationFormLinkService.link(jobId, teacherId, isDeveloper = false, linkRequest)

        // 학생 지원 가능 여부 확인
        val eligibility = jobApplicationService.checkEligibility(jobId, studentId)
        assertThat(eligibility.canApply).isTrue()
        assertThat(eligibility.availableActions).containsExactly("CREATE_DRAFT")

        // DRAFT 생성
        val createRequest = CreateJobApplicationRequest(prefillProfileFields = false)
        val draft = jobApplicationService.createDraft(jobId, studentId, createRequest)
        assertThat(draft.status).isEqualTo(JobApplicationStatus.DRAFT)
        val applicationId = draft.applicationId

        // 답변 임시저장 + 첨부파일 연결
        val fileId = createUploadedFile(studentId)
        jobApplicationService.saveDraft(
            applicationId,
            studentId,
            SaveJobApplicationDraftRequest(
                answers = listOf(motivationAnswer("열심히 하겠습니다"), resumeAnswer(fileId)),
                privacyConsent = true,
            ),
        )

        // SUBMIT
        val submitRequest = JobApplicationActionRequest(JobApplicationAction.SUBMIT)
        val submitted = jobApplicationService.executeAction(applicationId, studentId, submitRequest)
        assertThat(submitted.status).isEqualTo(JobApplicationStatus.SUBMITTED)
        assertThat(submitted.files.map { it.fileId }).containsExactly(fileId)

        // 교사 상세 조회(담당 공고 여부와 무관하게 모든 교사·개발자가 조회 가능, Phase 4 정책)
        val detail = jobApplicationAdminService.getDetail(applicationId)
        assertThat(detail.status).isEqualTo(JobApplicationStatus.SUBMITTED)

        // REQUEST_REVISION
        jobApplicationAdminService.executeAction(
            applicationId,
            teacherId,
            isDeveloper = false,
            JobApplicationAdminActionRequest(
                JobApplicationAdminAction.REQUEST_REVISION,
                reason = "포트폴리오 링크를 추가해주세요.",
            ),
        )

        // 학생 수정(REVISION_REQUESTED는 SAVE_DRAFT_ALLOWED_STATUSES에 포함)
        val revisedMotivation = "열심히 하겠습니다. 포트폴리오: https://example.com"
        jobApplicationService.saveDraft(
            applicationId,
            studentId,
            SaveJobApplicationDraftRequest(
                answers = listOf(motivationAnswer(revisedMotivation), resumeAnswer(fileId)),
            ),
        )

        // RESUBMIT
        val resubmitRequest = JobApplicationActionRequest(JobApplicationAction.RESUBMIT)
        val resubmitted = jobApplicationService.executeAction(applicationId, studentId, resubmitRequest)
        assertThat(resubmitted.status).isEqualTo(JobApplicationStatus.SUBMITTED)

        // 교사 APPROVE
        jobApplicationAdminService.executeAction(
            applicationId,
            teacherId,
            isDeveloper = false,
            JobApplicationAdminActionRequest(JobApplicationAdminAction.APPROVE),
        )
        assertThat(jobApplicationAdminService.getDetail(applicationId).status)
            .isEqualTo(JobApplicationStatus.APPROVED)

        // 학생 Notification 확인(Issue #135 AFTER_COMMIT Listener). REQUEST_REVISION도 같은
        // JobApplicationReviewedEvent를 발행하므로(모든 검토 Action이 공통으로 발행), 이 Flow가
        // 끝나면 학생에게는 REQUEST_REVISION 알림과 APPROVE 알림 2건이 쌓여 있다 -- 가장 최근
        // 알림(APPROVE)이 "승인"을 담고 있는지 확인한다.
        val notifications = awaitNotifications(studentId, expectedCount = 2)
        assertThat(notifications.first().content).contains("승인")
    }

    @Test
    fun `학생 수정 요청 Flow -- SUBMIT에서 REQUEST_EDIT으로 되돌아가 재제출한다`() {
        val teacherId = createMember("edit-flow-teacher")
        val studentId = createStudent("edit-flow-student")
        val applicationId = createSubmittedApplication(teacherId, studentId, "edit-flow")

        val requestEdit = JobApplicationActionRequest(JobApplicationAction.REQUEST_EDIT)
        jobApplicationService.executeAction(applicationId, studentId, requestEdit)
        assertThat(currentStatus(applicationId)).isEqualTo(JobApplicationStatus.EDIT_REQUESTED)

        jobApplicationAdminService.executeAction(
            applicationId,
            teacherId,
            isDeveloper = false,
            JobApplicationAdminActionRequest(JobApplicationAdminAction.ALLOW_EDIT),
        )
        assertThat(currentStatus(applicationId)).isEqualTo(JobApplicationStatus.EDIT_ALLOWED)

        jobApplicationService.saveDraft(
            applicationId,
            studentId,
            SaveJobApplicationDraftRequest(answers = listOf(motivationAnswer("수정된 답변"))),
        )

        val resubmit = JobApplicationActionRequest(JobApplicationAction.RESUBMIT)
        jobApplicationService.executeAction(applicationId, studentId, resubmit)
        assertThat(currentStatus(applicationId)).isEqualTo(JobApplicationStatus.SUBMITTED)
    }

    @Test
    fun `철회 Flow -- WITHDRAW가 허용된 모든 상태에서 철회할 수 있다`() {
        val teacherId = createMember("withdraw-flow-teacher")

        // DRAFT
        withdrawFrom(teacherId, "withdraw-draft") { studentId, jobId ->
            val request = CreateJobApplicationRequest(prefillProfileFields = false)
            jobApplicationService.createDraft(jobId, studentId, request).applicationId
        }

        // SUBMITTED
        withdrawFrom(teacherId, "withdraw-submitted") { studentId, _ ->
            createSubmittedApplication(teacherId, studentId, "withdraw-submitted")
        }

        // EDIT_REQUESTED
        withdrawFrom(teacherId, "withdraw-edit-requested") { studentId, _ ->
            val applicationId = createSubmittedApplication(teacherId, studentId, "withdraw-edit-requested")
            val requestEdit = JobApplicationActionRequest(JobApplicationAction.REQUEST_EDIT)
            jobApplicationService.executeAction(applicationId, studentId, requestEdit)
            applicationId
        }

        // EDIT_ALLOWED
        withdrawFrom(teacherId, "withdraw-edit-allowed") { studentId, _ ->
            val applicationId = createSubmittedApplication(teacherId, studentId, "withdraw-edit-allowed")
            val requestEdit = JobApplicationActionRequest(JobApplicationAction.REQUEST_EDIT)
            jobApplicationService.executeAction(applicationId, studentId, requestEdit)
            jobApplicationAdminService.executeAction(
                applicationId,
                teacherId,
                isDeveloper = false,
                JobApplicationAdminActionRequest(JobApplicationAdminAction.ALLOW_EDIT),
            )
            applicationId
        }

        // REVISION_REQUESTED
        withdrawFrom(teacherId, "withdraw-revision-requested") { studentId, _ ->
            val applicationId = createSubmittedApplication(teacherId, studentId, "withdraw-revision-requested")
            jobApplicationAdminService.executeAction(
                applicationId,
                teacherId,
                isDeveloper = false,
                JobApplicationAdminActionRequest(JobApplicationAdminAction.REQUEST_REVISION, reason = "보완 요청"),
            )
            applicationId
        }
    }

    @Test
    fun `권한 Flow -- 다른 학생 지원서 접근은 거부되고 담당 교사만 상태를 바꿀 수 있다`() {
        val managerTeacherId = createMember("permission-manager")
        val otherTeacherId = createMember("permission-other-teacher")
        val studentAId = createStudent("permission-student-a")
        val studentBId = createStudent("permission-student-b")
        val applicationId = createSubmittedApplication(managerTeacherId, studentAId, "permission")

        // 다른 학생(B)이 학생 A의 지원서를 수정하려 하면 거부된다.
        val editByOtherStudent = SaveJobApplicationDraftRequest(contactPhone = "010-0000-0000")
        assertThatThrownBy {
            jobApplicationService.saveDraft(applicationId, studentBId, editByOtherStudent)
        }.isInstanceOf(ApplicationAccessForbiddenException::class.java)

        // 담당 공고 여부와 무관하게 모든 교사가 조회할 수 있다(Phase 4 정책).
        assertThat(jobApplicationAdminService.getDetail(applicationId).applicationId).isEqualTo(applicationId)

        // 담당 아닌 교사의 상태 변경은 거부된다.
        assertThatThrownBy {
            jobApplicationAdminService.executeAction(
                applicationId,
                otherTeacherId,
                isDeveloper = false,
                JobApplicationAdminActionRequest(JobApplicationAdminAction.APPROVE),
            )
        }.isInstanceOf(ApplicationReviewForbiddenException::class.java)

        // 담당 교사의 상태 변경은 허용된다.
        jobApplicationAdminService.executeAction(
            applicationId,
            managerTeacherId,
            isDeveloper = false,
            JobApplicationAdminActionRequest(JobApplicationAdminAction.APPROVE),
        )
        assertThat(currentStatus(applicationId)).isEqualTo(JobApplicationStatus.APPROVED)
    }

    @Test
    fun `권한 Flow -- 개발자는 담당 교사가 아니어도 상태를 바꿀 수 있다`() {
        val managerTeacherId = createMember("permission-developer-manager")
        val studentId = createStudent("permission-developer-student")
        val applicationId = createSubmittedApplication(managerTeacherId, studentId, "permission-developer")

        jobApplicationAdminService.executeAction(
            applicationId,
            DEVELOPER_ID,
            isDeveloper = true,
            JobApplicationAdminActionRequest(JobApplicationAdminAction.REJECT, reason = "정책상 허용 범위 확인"),
        )

        assertThat(currentStatus(applicationId)).isEqualTo(JobApplicationStatus.REJECTED)
    }

    private fun motivationAnswer(text: String): ApplicationAnswer {
        val value = objectMapper.readTree("\"$text\"")
        return ApplicationAnswer(fieldId = "motivation", value = value)
    }

    private fun resumeAnswer(fileId: Long): ApplicationAnswer =
        ApplicationAnswer(fieldId = "resume", value = null, fileIds = listOf(fileId))

    /**
     * 서로 다른 학생·공고로 지원서를 [applicationId]까지 만든 뒤 WITHDRAW가 성공하는지 확인한다.
     * 각 상태를 매번 독립된 학생·공고로 만들어 Test끼리 섞이지 않게 한다.
     */
    private fun withdrawFrom(
        teacherId: Long,
        subject: String,
        toTargetStatus: (studentId: Long, jobId: Long) -> Long,
    ) {
        val studentId = createStudent(subject)
        val jobId = createJob(teacherId, subject)
        linkActiveForm(teacherId, jobId, subject)

        val applicationId = toTargetStatus(studentId, jobId)

        val withdraw = JobApplicationActionRequest(JobApplicationAction.WITHDRAW)
        jobApplicationService.executeAction(applicationId, studentId, withdraw)

        assertThat(currentStatus(applicationId))
            .`as`("subject=%s", subject)
            .isEqualTo(JobApplicationStatus.WITHDRAWN)
    }

    private fun currentStatus(applicationId: Long): JobApplicationStatus =
        jobApplicationAdminService.getDetail(applicationId).status

    /** Form 생성·연결부터 SUBMIT까지 이미 끝난 지원서를 만든다(각 Flow Test의 공통 준비 단계). */
    private fun createSubmittedApplication(
        teacherId: Long,
        studentId: Long,
        subject: String,
    ): Long {
        val jobId = createJob(teacherId, subject)
        linkActiveForm(teacherId, jobId, subject)
        val createRequest = CreateJobApplicationRequest(prefillProfileFields = false)
        val draft = jobApplicationService.createDraft(jobId, studentId, createRequest)
        val submitRequest = JobApplicationActionRequest(JobApplicationAction.SUBMIT)
        return jobApplicationService.executeAction(draft.applicationId, studentId, submitRequest).applicationId
    }

    private fun linkActiveForm(
        teacherId: Long,
        jobId: Long,
        subject: String,
    ) {
        val motivationField =
            FormFieldRequest(key = "motivation", type = FormFieldType.TEXT, label = "동기", required = false)
        val form =
            formService.create(
                teacherId,
                CreateFormRequest(
                    name = "$subject 지원서",
                    formType = FormType.JOB,
                    status = FormStatus.ACTIVE,
                    fields = listOf(motivationField),
                ),
            )
        val linkRequest = JobApplicationFormLinkRequest(form.formId)
        jobApplicationFormLinkService.link(jobId, teacherId, isDeveloper = false, linkRequest)
    }

    /**
     * [expectedCount]개가 쌓일 때까지 Polling한다. 검토 Action(ALLOW_EDIT/REQUEST_REVISION/
     * APPROVE/REJECT)은 모두 같은 JobApplicationReviewedEvent를 발행하므로(Issue #135), 한
     * 지원서에 여러 Action이 이어지면 그만큼 알림도 쌓인다 -- 정확한 개수까지 기다려야 마지막
     * Action의 알림만 생겼다고 성급하게 판단하지 않는다. 최신순(createdAt DESC)이라 `first()`가
     * 가장 최근 Action의 알림이다.
     */
    private fun awaitNotifications(
        memberId: Long,
        expectedCount: Int,
    ): List<Notification> {
        val deadline = System.nanoTime() + AWAIT_TIMEOUT_MILLIS * NANOS_PER_MILLI
        var notifications = notificationsOf(memberId)
        while (notifications.size < expectedCount && System.nanoTime() < deadline) {
            Thread.sleep(POLL_INTERVAL_MILLIS)
            notifications = notificationsOf(memberId)
        }
        assertThat(notifications)
            .`as`("%dms 안에 memberId=%d 알림이 %d건 생성되지 않았다", AWAIT_TIMEOUT_MILLIS, memberId, expectedCount)
            .hasSize(expectedCount)
        return notifications
    }

    private fun notificationsOf(memberId: Long): List<Notification> {
        val page =
            notificationRepository.findMyNotifications(
                memberId = memberId,
                isRead = null,
                type = null,
                pageable = PageRequest.of(0, PAGE_SIZE),
            )
        return page.content
    }

    private fun createStudent(subject: String): Long {
        val member =
            memberRepository.saveAndFlush(
                Member(
                    oauthProvider = OAuthProvider.DG,
                    oauthSubject = "e2e-$subject-${UUID.randomUUID()}",
                    email = "e2e-$subject-${UUID.randomUUID()}@example.com",
                    status = MemberStatus.ACTIVE,
                ).apply {
                    academicStatus = AcademicStatus.ENROLLED
                    grade = 2
                },
            )
        return requireNotNull(member.id)
    }

    private fun createMember(subject: String): Long {
        val member =
            memberRepository.saveAndFlush(
                Member(
                    oauthProvider = OAuthProvider.DG,
                    oauthSubject = "e2e-$subject-${UUID.randomUUID()}",
                    email = "e2e-$subject-${UUID.randomUUID()}@example.com",
                    status = MemberStatus.ACTIVE,
                ),
            )
        return requireNotNull(member.id)
    }

    private fun createJob(
        teacherId: Long,
        subject: String,
    ): Long {
        val company =
            companyRepository.saveAndFlush(
                Company(name = "E2E 기업 $subject ${UUID.randomUUID()}", type = CompanyType.GENERAL),
            )
        val job =
            jobRepository.saveAndFlush(
                Job(
                    companyId = requireNotNull(company.id),
                    type = PostingType.GENERAL,
                    applicationMethod = ApplicationMethod.INTERNAL,
                    title = "E2E Test 공고 $subject",
                    status = JobStatus.PUBLISHED,
                ).apply {
                    createdByMemberId = teacherId
                    managerMemberId = teacherId
                },
            )
        return requireNotNull(job.id)
    }

    private fun createUploadedFile(uploaderMemberId: Long): Long {
        val file =
            StoredFile(
                purpose = FilePurpose.JOB_APPLICATION,
                objectKey = "JOB_APPLICATION/e2e/${UUID.randomUUID()}",
                originalName = "resume.pdf",
                contentType = "application/pdf",
                sizeBytes = 1024,
                uploaderMemberId = uploaderMemberId,
                extension = "pdf",
            ).apply { markUploaded() }
        return requireNotNull(storedFileRepository.saveAndFlush(file).id)
    }

    companion object {
        private const val PAGE_SIZE = 20
        private const val AWAIT_TIMEOUT_MILLIS = 5_000L
        private const val POLL_INTERVAL_MILLIS = 50L
        private const val NANOS_PER_MILLI = 1_000_000L
        private const val DEVELOPER_ID = 999L

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
