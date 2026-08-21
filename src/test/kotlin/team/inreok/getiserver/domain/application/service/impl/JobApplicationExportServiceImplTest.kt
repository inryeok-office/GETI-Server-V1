package team.inreok.getiserver.domain.application.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import team.inreok.getiserver.domain.application.entity.JobApplication
import team.inreok.getiserver.domain.application.entity.JobApplicationSubmission
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus
import team.inreok.getiserver.domain.application.exception.ApplicationReviewForbiddenException
import team.inreok.getiserver.domain.application.exception.JobNotFoundException
import team.inreok.getiserver.domain.application.repository.JobApplicationRepository
import team.inreok.getiserver.domain.application.repository.JobApplicationSubmissionRepository
import team.inreok.getiserver.domain.application.service.JobApplicationExportService
import team.inreok.getiserver.domain.file.archive.FileArchiveEntry
import team.inreok.getiserver.domain.file.archive.FileArchivePort
import team.inreok.getiserver.domain.file.archive.FileArchiveResult
import team.inreok.getiserver.domain.file.link.FileLinkPort
import team.inreok.getiserver.domain.file.link.FileSnapshot
import team.inreok.getiserver.domain.job.query.JobApplicationJobSnapshot
import team.inreok.getiserver.domain.job.query.JobApplicationSnapshotQueryPort
import tools.jackson.databind.json.JsonMapper
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime

/**
 * Issue #138. 핵심은 "현재 지원서가 아닌 제출 Snapshot 기준"으로 대상 File을 모으는 것과,
 * 권한 판정(등록자·담당 교사·개발자만)이다. 실제 ZIP 생성은 FileArchivePort(Issue #154)의
 * 책임이라 여기서는 Mock으로 대체하고, 이 Service가 올바른 fileId·표시 이름을 넘기는지만 본다.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobApplicationExportServiceImplTest {
    @Mock
    private lateinit var jobApplicationRepository: JobApplicationRepository

    @Mock
    private lateinit var jobApplicationSubmissionRepository: JobApplicationSubmissionRepository

    @Mock
    private lateinit var jobApplicationSnapshotQueryPort: JobApplicationSnapshotQueryPort

    @Mock
    private lateinit var fileLinkPort: FileLinkPort

    @Mock
    private lateinit var fileArchivePort: FileArchivePort

    private val service: JobApplicationExportService by lazy {
        JobApplicationExportServiceImpl(
            jobApplicationRepository,
            jobApplicationSubmissionRepository,
            jobApplicationSnapshotQueryPort,
            fileLinkPort,
            fileArchivePort,
            JsonMapper(),
        )
    }

    @Test
    fun `등록자가 아니어도 담당 교사면 대상 지원자의 최신 제출 파일을 모은다`() {
        val application = applicationOf(id = 1L, applicantName = "홍길동")
        givenApplications(application)
        givenJob(createdByMemberId = 100L, managerMemberId = MANAGER_ID)
        givenSubmission(applicationId = 1L, fileIds = listOf(10L, 11L))
        given(fileLinkPort.snapshotsOf(setOf(10L, 11L)))
            .willReturn(
                mapOf(
                    10L to fileSnapshotOf(10L, "resume.pdf"),
                    11L to fileSnapshotOf(11L, "portfolio.pdf"),
                ),
            )

        val entries = service.buildExportEntries(JOB_ID, MANAGER_ID, isDeveloper = false)

        assertThat(entries).containsExactlyInAnyOrder(
            FileArchiveEntry(10L, "홍길동_resume.pdf"),
            FileArchiveEntry(11L, "홍길동_portfolio.pdf"),
        )
    }

    @Test
    fun `개발자는 담당자가 아니어도 조회할 수 있다`() {
        val application = applicationOf(id = 1L, applicantName = "홍길동")
        givenApplications(application)
        givenSubmission(applicationId = 1L, fileIds = listOf(10L))
        given(fileLinkPort.snapshotsOf(setOf(10L))).willReturn(mapOf(10L to fileSnapshotOf(10L, "resume.pdf")))

        val entries = service.buildExportEntries(JOB_ID, OTHER_MEMBER_ID, isDeveloper = true)

        assertThat(entries).containsExactly(FileArchiveEntry(10L, "홍길동_resume.pdf"))
    }

    @Test
    fun `담당자가 아닌 교사는 거부된다`() {
        givenJob(createdByMemberId = 100L, managerMemberId = MANAGER_ID)

        assertThatThrownBy {
            service.buildExportEntries(JOB_ID, OTHER_MEMBER_ID, isDeveloper = false)
        }.isInstanceOf(ApplicationReviewForbiddenException::class.java)
    }

    @Test
    fun `공고를 찾을 수 없으면 거부된다`() {
        given(jobApplicationSnapshotQueryPort.findById(JOB_ID)).willReturn(null)

        assertThatThrownBy {
            service.buildExportEntries(JOB_ID, OTHER_MEMBER_ID, isDeveloper = false)
        }.isInstanceOf(JobNotFoundException::class.java)
    }

    @Test
    fun `applicantName이 비어 있으면 지원서 ID로 대체한다`() {
        val application = applicationOf(id = 1L, applicantName = null)
        givenApplications(application)
        givenJob(createdByMemberId = MANAGER_ID)
        givenSubmission(applicationId = 1L, fileIds = listOf(10L))
        given(fileLinkPort.snapshotsOf(setOf(10L))).willReturn(mapOf(10L to fileSnapshotOf(10L, "resume.pdf")))

        val entries = service.buildExportEntries(JOB_ID, MANAGER_ID, isDeveloper = false)

        assertThat(entries).containsExactly(FileArchiveEntry(10L, "지원자1_resume.pdf"))
    }

    @Test
    fun `제출 Snapshot이 없는 지원자는 건너뛴다`() {
        val application = applicationOf(id = 1L, applicantName = "홍길동")
        givenApplications(application)
        givenJob(createdByMemberId = MANAGER_ID)
        given(jobApplicationSubmissionRepository.findLatestByApplicationIdIn(setOf(1L))).willReturn(emptyList())

        val entries = service.buildExportEntries(JOB_ID, MANAGER_ID, isDeveloper = false)

        assertThat(entries).isEmpty()
    }

    @Test
    fun `존재하지 않거나 보이지 않는 File은 결과에서 빠진다`() {
        val application = applicationOf(id = 1L, applicantName = "홍길동")
        givenApplications(application)
        givenJob(createdByMemberId = MANAGER_ID)
        givenSubmission(applicationId = 1L, fileIds = listOf(10L, 999L))
        given(fileLinkPort.snapshotsOf(setOf(10L, 999L)))
            .willReturn(mapOf(10L to fileSnapshotOf(10L, "resume.pdf")))

        val entries = service.buildExportEntries(JOB_ID, MANAGER_ID, isDeveloper = false)

        assertThat(entries).containsExactly(FileArchiveEntry(10L, "홍길동_resume.pdf"))
    }

    @Test
    fun `지원자가 여럿이어도 제출 Snapshot을 한 번에 배치 조회한다`() {
        // PR #157 코드리뷰 반영 -- 지원자 수만큼 단건 조회를 반복하지 않는지 확인한다(N+1 방지).
        val first = applicationOf(id = 1L, applicantName = "홍길동")
        val second = applicationOf(id = 2L, applicantName = "김철수")
        givenApplications(first, second)
        givenJob(createdByMemberId = MANAGER_ID)
        given(jobApplicationSubmissionRepository.findLatestByApplicationIdIn(setOf(1L, 2L)))
            .willReturn(
                listOf(
                    submissionOf(applicationId = 1L, fileIds = listOf(10L)),
                    submissionOf(applicationId = 2L, fileIds = listOf(20L)),
                ),
            )
        given(fileLinkPort.snapshotsOf(setOf(10L, 20L)))
            .willReturn(mapOf(10L to fileSnapshotOf(10L, "resume.pdf"), 20L to fileSnapshotOf(20L, "resume.pdf")))

        val entries = service.buildExportEntries(JOB_ID, MANAGER_ID, isDeveloper = false)

        assertThat(entries).containsExactlyInAnyOrder(
            FileArchiveEntry(10L, "홍길동_resume.pdf"),
            FileArchiveEntry(20L, "김철수_resume.pdf"),
        )
        // applicationId별 단건 조회(findTopByApplicationIdOrderBySubmissionNumberDesc)는
        // 전혀 호출되지 않는다 -- 배치 Method 하나로만 조회한다.
        org.mockito.Mockito
            .verify(jobApplicationSubmissionRepository, org.mockito.Mockito.never())
            .findTopByApplicationIdOrderBySubmissionNumberDesc(org.mockito.ArgumentMatchers.anyLong())
    }

    @Test
    fun `writeZip은 FileArchivePort에 그대로 위임한다`() {
        val entries = listOf(FileArchiveEntry(1L, "a.pdf"))
        val output = ByteArrayOutputStream()
        given(fileArchivePort.writeZip(entries, output)).willReturn(FileArchiveResult(listOf(1L), emptyList()))

        service.writeZip(entries, output)

        org.mockito.Mockito
            .verify(fileArchivePort)
            .writeZip(entries, output)
    }

    private fun givenApplications(vararg applications: JobApplication) {
        given(
            jobApplicationRepository.search(
                jobId = JOB_ID,
                status = null,
                hasApplicantName = false,
                applicantName = "",
                cohort = null,
                department = null,
                hasJobFilter = false,
                jobIds = emptySet(),
                pageable = Pageable.unpaged(),
            ),
        ).willReturn(PageImpl(applications.toList()))
    }

    private fun givenSubmission(
        applicationId: Long,
        fileIds: List<Long>,
    ) {
        given(jobApplicationSubmissionRepository.findLatestByApplicationIdIn(setOf(applicationId)))
            .willReturn(listOf(submissionOf(applicationId = applicationId, fileIds = fileIds)))
    }

    private fun givenJob(
        createdByMemberId: Long? = null,
        managerMemberId: Long? = null,
    ) {
        given(jobApplicationSnapshotQueryPort.findById(JOB_ID)).willReturn(
            JobApplicationJobSnapshot(
                jobId = JOB_ID,
                title = "인턴 채용",
                companyId = 1L,
                postingType = "MOU",
                applicationMethod = "INTERNAL",
                status = "PUBLISHED",
                targetGrade = null,
                recruitmentStartedAt = null,
                recruitmentEndedAt = null,
                createdByMemberId = createdByMemberId,
                managerMemberId = managerMemberId,
            ),
        )
    }

    private fun applicationOf(
        id: Long,
        applicantName: String?,
    ) = JobApplication(
        jobId = JOB_ID,
        applicantMemberId = 7L,
        attemptNumber = 1,
        contactEmail = "student@example.com",
        answers = "[]",
        status = JobApplicationStatus.SUBMITTED,
    ).apply {
        this.id = id
        this.applicantName = applicantName
    }

    private fun submissionOf(
        applicationId: Long,
        fileIds: List<Long>,
    ) = JobApplicationSubmission(
        applicationId = applicationId,
        submissionNumber = 1,
        formId = 1L,
        formVersion = 1,
        answers = """[{"fieldId":"resume","fileIds":$fileIds}]""",
        submittedAt = LocalDateTime.of(2026, 3, 1, 10, 0, 0),
    )

    private fun fileSnapshotOf(
        fileId: Long,
        originalName: String,
    ) = FileSnapshot(fileId = fileId, originalName = originalName, contentType = "application/pdf", size = 100)

    private companion object {
        const val JOB_ID = 1L
        const val MANAGER_ID = 100L
        const val OTHER_MEMBER_ID = 999L
    }
}
