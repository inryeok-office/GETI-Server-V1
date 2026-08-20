package team.inreok.getiserver.domain.application.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import team.inreok.getiserver.domain.application.entity.JobApplication
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus
import team.inreok.getiserver.domain.application.exception.ApplicationAccessForbiddenException
import team.inreok.getiserver.domain.application.exception.ApplicationNotFoundException
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
import team.inreok.getiserver.domain.job.access.JobAiSkillAccessView
import team.inreok.getiserver.domain.job.access.JobBookmarkAccessor
import team.inreok.getiserver.domain.job.query.JobApplicationJobSnapshot
import team.inreok.getiserver.domain.job.query.JobApplicationSnapshotQueryPort
import team.inreok.getiserver.domain.member.query.MemberApplicantSnapshotQueryPort
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime
import java.util.Optional

/**
 * `JobApplicationServiceImpl.list`/`getDetail`(본인 지원 목록·상세, Issue #184) Test다.
 * `JobApplicationServiceImplTest`에서 분리했다(detekt LargeClass 회피 목적,
 * `JobApplicationFileSyncTest` 분리와 같은 이유) -- Mock 구성은 같은 Class를 그대로 재구성한다.
 */
@ExtendWith(MockitoExtension::class)
class JobApplicationServiceImplQueryTest {
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
            JsonMapper(),
        )
    }

    private val fixedTime = LocalDateTime.of(2026, 3, 1, 10, 0, 0)

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

    private fun draftOf(
        id: Long = 1L,
        applicantMemberId: Long = 1L,
        status: JobApplicationStatus = JobApplicationStatus.DRAFT,
    ) = JobApplication(
        jobId = 1L,
        applicantMemberId = applicantMemberId,
        attemptNumber = 1,
        contactEmail = "student@example.com",
        answers = "[]",
        status = status,
    ).apply {
        this.id = id
        createdAt = fixedTime
        updatedAt = fixedTime
    }

    // ---------- list ----------

    @Test
    fun `본인 지원 목록을 조회하면 공고 요약과 북마크 여부가 채워진다`() {
        val page = PageImpl(listOf(draftOf(status = JobApplicationStatus.SUBMITTED)), PageRequest.of(0, 20), 1)
        given(jobApplicationRepository.searchForApplicant(1L, null, PageRequest.of(0, 20))).willReturn(page)
        given(jobApplicationSnapshotQueryPort.findAllByIds(setOf(1L))).willReturn(mapOf(1L to jobOf()))
        given(companyQuery.findActiveSummaries(setOf(1L), 1L))
            .willReturn(mapOf(1L to CompanySummary(companyId = 1L, name = "인력개발원")))
        given(jobBookmarkAccessor.findAllByJobIds(setOf(1L), 1L)).willReturn(setOf(1L))
        given(jobBookmarkAccessor.countAllByJobIds(setOf(1L))).willReturn(mapOf(1L to 7L))
        given(jobAiAnalysisAccessor.findMatchedTechStacks(setOf(1L)))
            .willReturn(mapOf(1L to listOf(JobAiSkillAccessView(techStackId = 3L, name = "Kotlin"))))

        val result = service.list(1L, null, PageRequest.of(0, 20))

        assertThat(result.content).hasSize(1)
        val item = result.content[0]
        assertThat(item.applicationId).isEqualTo(1L)
        assertThat(item.status).isEqualTo(JobApplicationStatus.SUBMITTED)
        val job = requireNotNull(item.job)
        assertThat(job.title).isEqualTo("인턴 채용")
        assertThat(job.company?.name).isEqualTo("인력개발원")
        assertThat(job.bookmarked).isTrue()
        assertThat(job.bookmarkCount).isEqualTo(7L)
        assertThat(job.techStacks).containsExactly(JobAiSkillAccessView(techStackId = 3L, name = "Kotlin"))
    }

    @Test
    fun `공고가 삭제되어 조회할 수 없으면 목록 항목의 job은 null이다`() {
        val page = PageImpl(listOf(draftOf()), PageRequest.of(0, 20), 1)
        given(jobApplicationRepository.searchForApplicant(1L, null, PageRequest.of(0, 20))).willReturn(page)
        given(jobApplicationSnapshotQueryPort.findAllByIds(setOf(1L))).willReturn(emptyMap())

        val result = service.list(1L, null, PageRequest.of(0, 20))

        assertThat(result.content[0].job).isNull()
    }

    // ---------- getDetail ----------

    @Test
    fun `본인 지원서 상세를 조회하면 공고명 기업명 현재 수행 가능한 Action이 채워진다`() {
        given(jobApplicationRepository.findById(1L))
            .willReturn(Optional.of(draftOf(status = JobApplicationStatus.SUBMITTED)))
        given(jobApplicationSnapshotQueryPort.findById(1L)).willReturn(jobOf())
        given(companyQuery.findActiveSummary(1L, 1L))
            .willReturn(CompanySummary(companyId = 1L, name = "인력개발원"))
        given(fileLinkPort.linkedFilesOf(FileOwnerType.JOB_APPLICATION, 1L)).willReturn(emptyList())

        val result = service.getDetail(1L, 1L)

        assertThat(result.jobTitle).isEqualTo("인턴 채용")
        assertThat(result.companyName).isEqualTo("인력개발원")
        assertThat(result.availableActions).containsExactly("REQUEST_EDIT", "WITHDRAW")
    }

    @Test
    fun `본인의 DRAFT 지원서 상세도 조회할 수 있다`() {
        given(jobApplicationRepository.findById(1L)).willReturn(Optional.of(draftOf()))
        given(jobApplicationSnapshotQueryPort.findById(1L)).willReturn(jobOf())
        given(companyQuery.findActiveSummary(1L, 1L))
            .willReturn(CompanySummary(companyId = 1L, name = "인력개발원"))
        given(fileLinkPort.linkedFilesOf(FileOwnerType.JOB_APPLICATION, 1L)).willReturn(emptyList())

        val result = service.getDetail(1L, 1L)

        assertThat(result.status).isEqualTo(JobApplicationStatus.DRAFT)
        assertThat(result.availableActions).containsExactly("SUBMIT", "WITHDRAW")
    }

    @Test
    fun `다른 학생의 지원서 상세를 조회하면 ApplicationAccessForbiddenException을 던진다`() {
        given(jobApplicationRepository.findById(1L)).willReturn(Optional.of(draftOf(applicantMemberId = 1L)))

        assertThatThrownBy { service.getDetail(1L, 2L) }.isInstanceOf(ApplicationAccessForbiddenException::class.java)
    }

    @Test
    fun `존재하지 않는 지원서 상세를 조회하면 ApplicationNotFoundException을 던진다`() {
        given(jobApplicationRepository.findById(999L)).willReturn(Optional.empty())

        assertThatThrownBy { service.getDetail(999L, 1L) }.isInstanceOf(ApplicationNotFoundException::class.java)
    }
}
