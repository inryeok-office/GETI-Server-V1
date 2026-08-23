package team.inreok.getiserver.domain.application.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.application.dto.ApplicationAnswer
import team.inreok.getiserver.domain.application.dto.CreateJobApplicationRequest
import team.inreok.getiserver.domain.application.dto.FormFieldSchema
import team.inreok.getiserver.domain.application.dto.JobApplicationAction
import team.inreok.getiserver.domain.application.dto.JobApplicationActionRequest
import team.inreok.getiserver.domain.application.dto.SaveJobApplicationDraftRequest
import team.inreok.getiserver.domain.application.entity.Form
import team.inreok.getiserver.domain.application.entity.FormVersion
import team.inreok.getiserver.domain.application.entity.JobApplication
import team.inreok.getiserver.domain.application.entity.JobApplicationForm
import team.inreok.getiserver.domain.application.entity.JobApplicationStatusHistory
import team.inreok.getiserver.domain.application.entity.type.FormFieldType
import team.inreok.getiserver.domain.application.entity.type.FormStatus
import team.inreok.getiserver.domain.application.entity.type.FormType
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus
import team.inreok.getiserver.domain.application.repository.FormRepository
import team.inreok.getiserver.domain.application.repository.FormVersionRepository
import team.inreok.getiserver.domain.application.repository.JobApplicationFormRepository
import team.inreok.getiserver.domain.application.repository.JobApplicationRepository
import team.inreok.getiserver.domain.application.repository.JobApplicationStatusHistoryRepository
import team.inreok.getiserver.domain.application.repository.JobApplicationSubmissionRepository
import team.inreok.getiserver.domain.application.service.JobApplicationService
import team.inreok.getiserver.domain.company.query.CompanyQuery
import team.inreok.getiserver.domain.company.query.CompanySummary
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.file.link.FileLinkPort
import team.inreok.getiserver.domain.job.access.JobAiAnalysisAccessor
import team.inreok.getiserver.domain.job.access.JobBookmarkAccessor
import team.inreok.getiserver.domain.job.query.JobApplicationJobSnapshot
import team.inreok.getiserver.domain.job.query.JobApplicationSnapshotQueryPort
import team.inreok.getiserver.domain.member.query.MemberApplicantSnapshot
import team.inreok.getiserver.domain.member.query.MemberApplicantSnapshotQueryPort
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class JobApplicationFormQuestionsTest {
    @Mock
    private lateinit var jobApplicationRepository: JobApplicationRepository

    @Mock
    private lateinit var jobApplicationFormRepository: JobApplicationFormRepository

    @Mock
    private lateinit var formRepository: FormRepository

    @Mock
    private lateinit var formVersionRepository: FormVersionRepository

    @Mock
    private lateinit var jobApplicationSnapshotQueryPort: JobApplicationSnapshotQueryPort

    @Mock
    private lateinit var memberApplicantSnapshotQueryPort: MemberApplicantSnapshotQueryPort

    @Mock
    private lateinit var jobApplicationStatusHistoryRepository: JobApplicationStatusHistoryRepository

    @Mock
    private lateinit var jobApplicationSubmissionRepository: JobApplicationSubmissionRepository

    @Mock
    private lateinit var fileLinkPort: FileLinkPort

    @Mock
    private lateinit var companyQuery: CompanyQuery

    @Mock
    private lateinit var jobBookmarkAccessor: JobBookmarkAccessor

    @Mock
    private lateinit var jobAiAnalysisAccessor: JobAiAnalysisAccessor

    private val jsonMapper = JsonMapper()
    private val fixedTime = LocalDateTime.of(2026, 3, 1, 10, 0, 0)

    private val service: JobApplicationService by lazy {
        JobApplicationServiceImpl(
            jobApplicationRepository,
            jobApplicationFormRepository,
            formRepository,
            formVersionRepository,
            jobApplicationSnapshotQueryPort,
            memberApplicantSnapshotQueryPort,
            jobApplicationStatusHistoryRepository,
            jobApplicationSubmissionRepository,
            fileLinkPort,
            companyQuery,
            jobBookmarkAccessor,
            jobAiAnalysisAccessor,
            jsonMapper,
        )
    }

    @Test
    fun `초안 생성 응답에 적용된 Form Version의 다섯 가지 문항 구조가 포함된다`() {
        given(jobApplicationSnapshotQueryPort.findById(1L)).willReturn(jobOf())
        given(memberApplicantSnapshotQueryPort.findById(1L)).willReturn(memberOf())
        stubActiveFormLink()
        given(
            jobApplicationRepository.findByJobIdAndApplicantMemberIdAndStatusIn(
                1L,
                1L,
                ACTIVE_JOB_APPLICATION_STATUSES,
            ),
        ).willReturn(emptyList())
        given(jobApplicationRepository.findTopByJobIdAndApplicantMemberIdOrderByAttemptNumberDesc(1L, 1L))
            .willReturn(null)
        given(formVersionRepository.findByFormIdAndVersion(10L, 2)).willReturn(formVersionWithAllTypesOf())
        given(jobApplicationRepository.saveAndFlush(any(JobApplication::class.java))).willAnswer { invocation ->
            (invocation.arguments[0] as JobApplication).apply {
                id = 1L
                createdAt = fixedTime
                updatedAt = fixedTime
            }
        }

        val result = service.createDraft(1L, 1L, CreateJobApplicationRequest())

        assertThat(result.questions).hasSize(5)
        assertThat(result.questions.map { it.type })
            .containsExactly(
                FormFieldType.TEXT,
                FormFieldType.TEXTAREA,
                FormFieldType.SINGLE_SELECT,
                FormFieldType.MULTI_SELECT,
                FormFieldType.FILE,
            )
        assertThat(result.questions[0].required).isTrue()
        assertThat(result.questions[2].options).containsExactly("A", "B")
        assertThat(
            result.questions[4]
                .filePolicy
                ?.get("maxSizeBytes")
                ?.asInt(),
        ).isEqualTo(10485760)
        assertThat(
            result.questions[4]
                .filePolicy
                ?.get("maxCount")
                ?.asInt(),
        ).isEqualTo(5)
    }

    @Test
    fun `임시저장 응답의 answers fieldId가 같은 Form Version questions와 연결된다`() {
        val application = applicationOf(formId = 10L, formVersion = 1)
        given(jobApplicationRepository.findById(1L)).willReturn(Optional.of(application))
        given(formVersionRepository.findByFormIdAndVersion(10L, 1)).willReturn(formVersionOf())
        given(fileLinkPort.linkedFilesOf(FileOwnerType.JOB_APPLICATION, 1L)).willReturn(emptyList())

        val result =
            service.saveDraft(
                1L,
                1L,
                SaveJobApplicationDraftRequest(
                    answers = listOf(ApplicationAnswer(fieldId = "motivation", value = null)),
                ),
            )

        assertThat(result.formVersion).isEqualTo(1)
        assertThat(result.questions).extracting<String> { it.fieldId }.containsExactly("motivation")
        assertThat(result.answers).extracting<String> { it.fieldId }.containsExactly("motivation")
    }

    @Test
    fun `Action 응답도 지원서에 저장된 Form Version의 questions를 반환한다`() {
        val application = applicationOf(formId = 10L, formVersion = 1)
        given(jobApplicationRepository.findByIdForUpdate(1L)).willReturn(application)
        given(formVersionRepository.findByFormIdAndVersion(10L, 1)).willReturn(formVersionOf())
        given(fileLinkPort.linkedFilesOf(FileOwnerType.JOB_APPLICATION, 1L)).willReturn(emptyList())
        // recordStatusHistory가 저장된 이력을 반환하도록 바뀌면서(Issue #193) executeAction이 이
        // 반환값을 실제로 역참조한다(JobApplicationAdminServiceImplTest.stubStatusHistorySave와
        // 같은 이유). 이 Test는 questions 매핑이 관심사라 이력 자체는 검증하지 않는다.
        given(jobApplicationStatusHistoryRepository.save(any(JobApplicationStatusHistory::class.java)))
            .willAnswer { invocation ->
                invocation.getArgument<JobApplicationStatusHistory>(0).apply { id = 999L }
            }

        val result =
            service.executeAction(
                1L,
                1L,
                JobApplicationActionRequest(JobApplicationAction.WITHDRAW),
            )

        assertThat(result.status).isEqualTo(JobApplicationStatus.WITHDRAWN)
        assertThat(result.questions[0].fieldId).isEqualTo("motivation")
    }

    @Test
    fun `본인 상세 응답도 지원서에 저장된 Form Version의 questions를 반환한다`() {
        val application = applicationOf(formId = 10L, formVersion = 1)
        given(jobApplicationRepository.findById(1L)).willReturn(Optional.of(application))
        given(jobApplicationSnapshotQueryPort.findById(1L)).willReturn(jobOf())
        given(companyQuery.findActiveSummary(1L, 1L))
            .willReturn(CompanySummary(companyId = 1L, name = "인력개발원"))
        given(fileLinkPort.linkedFilesOf(FileOwnerType.JOB_APPLICATION, 1L)).willReturn(emptyList())
        given(formVersionRepository.findByFormIdAndVersion(10L, 1)).willReturn(formVersionOf())

        val result = service.getDetail(1L, 1L)

        assertThat(result.formVersion).isEqualTo(1)
        assertThat(result.questions[0].fieldId).isEqualTo("motivation")
    }

    private fun stubActiveFormLink() {
        given(jobApplicationFormRepository.findById(1L))
            .willReturn(Optional.of(JobApplicationForm(jobId = 1L, formId = 10L, linkedByMemberId = 200L)))
        given(formRepository.findById(10L))
            .willReturn(
                Optional.of(
                    Form(ownerMemberId = 200L, name = "지원서", formType = FormType.JOB, status = FormStatus.ACTIVE)
                        .apply {
                            id = 10L
                            currentVersion = 2
                        },
                ),
            )
    }

    private fun applicationOf(
        formId: Long,
        formVersion: Int,
        status: JobApplicationStatus = JobApplicationStatus.DRAFT,
    ) = JobApplication(
        jobId = 1L,
        applicantMemberId = 1L,
        attemptNumber = 1,
        contactEmail = "student@example.com",
        answers = "[]",
        status = status,
    ).apply {
        id = 1L
        this.formId = formId
        this.formVersion = formVersion
        createdAt = fixedTime
        updatedAt = fixedTime
    }

    private fun formVersionOf() =
        FormVersion(
            formId = 10L,
            version = 1,
            schemaData =
                jsonMapper.writeValueAsString(
                    listOf(
                        FormFieldSchema(
                            key = "motivation",
                            type = FormFieldType.TEXTAREA,
                            label = "지원 동기",
                            description = "지원 동기를 작성해주세요.",
                            required = true,
                            order = 0,
                            options = null,
                            filePolicy = null,
                        ),
                    ),
                ),
        )

    private fun jobOf() =
        JobApplicationJobSnapshot(
            jobId = 1L,
            title = "인턴 채용",
            companyId = 1L,
            postingType = "MOU",
            applicationMethod = "INTERNAL",
            status = "PUBLISHED",
            targetGrade = null,
            recruitmentStartedAt = null,
            recruitmentEndedAt = null,
            createdByMemberId = 100L,
            managerMemberId = null,
        )

    private fun memberOf() =
        MemberApplicantSnapshot(
            memberId = 1L,
            name = "홍길동",
            email = "student@example.com",
            phone = null,
            academicStatus = "ENROLLED",
            grade = 3,
            cohort = 10,
            department = "SW_DEVELOPMENT",
            majors = emptyList(),
            techStacks = emptyList(),
            desiredJob = "Backend Developer",
        )
}
