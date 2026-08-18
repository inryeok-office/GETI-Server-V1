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
import org.springframework.data.domain.PageRequest
import team.inreok.getiserver.domain.application.entity.JobApplication
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus
import team.inreok.getiserver.domain.application.exception.ApplicationReviewForbiddenException
import team.inreok.getiserver.domain.application.exception.JobNotFoundException
import team.inreok.getiserver.domain.application.repository.JobApplicationRepository
import team.inreok.getiserver.domain.application.service.JobApplicationApplicantService
import team.inreok.getiserver.domain.file.link.FileUrlPort
import team.inreok.getiserver.domain.job.query.JobApplicationJobSnapshot
import team.inreok.getiserver.domain.job.query.JobApplicationSnapshotQueryPort
import team.inreok.getiserver.domain.member.query.ApplicationApplicantProfile
import team.inreok.getiserver.domain.member.query.ApplicationApplicantProfileQueryPort

/**
 * Issue #137. 핵심은 권한 판정(등록자·담당 교사·개발자만)과, Member/File 조회를 배치로 묶어
 * N+1을 만들지 않는 것이다.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobApplicationApplicantServiceImplTest {
    @Mock
    private lateinit var jobApplicationRepository: JobApplicationRepository

    @Mock
    private lateinit var jobApplicationSnapshotQueryPort: JobApplicationSnapshotQueryPort

    @Mock
    private lateinit var applicationApplicantProfileQueryPort: ApplicationApplicantProfileQueryPort

    @Mock
    private lateinit var fileUrlPort: FileUrlPort

    private val service: JobApplicationApplicantService by lazy {
        JobApplicationApplicantServiceImpl(
            jobApplicationRepository,
            jobApplicationSnapshotQueryPort,
            applicationApplicantProfileQueryPort,
            fileUrlPort,
        )
    }

    @Test
    fun `담당 교사는 지원자 목록을 조회하고 프로필 이미지 URL을 함께 받는다`() {
        givenJob(createdByMemberId = 100L)
        val application = applicationOf(id = 1L, applicantMemberId = 7L)
        givenApplications(application)
        given(applicationApplicantProfileQueryPort.findAllByIds(setOf(7L)))
            .willReturn(mapOf(7L to profileOf(memberId = 7L, profileImageFileId = 50L)))
        given(fileUrlPort.presignedImageUrls(100L, setOf(50L))).willReturn(mapOf(50L to "https://cdn.example.com/50"))

        val result = service.list(JOB_ID, requesterMemberId = 100L, isDeveloper = false, pageable = PAGEABLE)

        assertThat(result.content).hasSize(1)
        val item = result.content.first()
        assertThat(item.applicationId).isEqualTo(1L)
        assertThat(item.memberId).isEqualTo(7L)
        assertThat(item.name).isEqualTo("홍길동")
        assertThat(item.profileImageUrl).isEqualTo("https://cdn.example.com/50")
        assertThat(item.cohort).isEqualTo(10)
        assertThat(item.department).isEqualTo("SW_DEVELOPMENT")
    }

    @Test
    fun `프로필 이미지가 없는 지원자는 File Port를 호출하지 않고 null을 반환한다`() {
        givenJob(createdByMemberId = 100L)
        givenApplications(applicationOf(id = 1L, applicantMemberId = 7L))
        given(applicationApplicantProfileQueryPort.findAllByIds(setOf(7L)))
            .willReturn(mapOf(7L to profileOf(memberId = 7L, profileImageFileId = null)))

        val result = service.list(JOB_ID, requesterMemberId = 100L, isDeveloper = false, pageable = PAGEABLE)

        assertThat(result.content.first().profileImageUrl).isNull()
        org.mockito.Mockito.verifyNoInteractions(fileUrlPort)
    }

    @Test
    fun `Member Snapshot이 없는 지원자는 null 필드로 채운다`() {
        givenJob(createdByMemberId = 100L)
        givenApplications(applicationOf(id = 1L, applicantMemberId = 7L))
        given(applicationApplicantProfileQueryPort.findAllByIds(setOf(7L))).willReturn(emptyMap())

        val result = service.list(JOB_ID, requesterMemberId = 100L, isDeveloper = false, pageable = PAGEABLE)

        val item = result.content.first()
        assertThat(item.name).isNull()
        assertThat(item.profileImageUrl).isNull()
        assertThat(item.cohort).isNull()
        assertThat(item.department).isNull()
    }

    @Test
    fun `개발자는 담당자가 아니어도 조회할 수 있다`() {
        givenApplications(applicationOf(id = 1L, applicantMemberId = 7L))
        given(applicationApplicantProfileQueryPort.findAllByIds(setOf(7L))).willReturn(emptyMap())

        val result = service.list(JOB_ID, requesterMemberId = OTHER_MEMBER_ID, isDeveloper = true, pageable = PAGEABLE)

        assertThat(result.content).hasSize(1)
        org.mockito.Mockito.verifyNoInteractions(jobApplicationSnapshotQueryPort)
    }

    @Test
    fun `담당자가 아닌 교사는 거부된다`() {
        givenJob(createdByMemberId = 100L)

        assertThatThrownBy {
            service.list(JOB_ID, requesterMemberId = OTHER_MEMBER_ID, isDeveloper = false, pageable = PAGEABLE)
        }.isInstanceOf(ApplicationReviewForbiddenException::class.java)
    }

    @Test
    fun `공고를 찾을 수 없으면 거부된다`() {
        given(jobApplicationSnapshotQueryPort.findById(JOB_ID)).willReturn(null)

        assertThatThrownBy {
            service.list(JOB_ID, requesterMemberId = OTHER_MEMBER_ID, isDeveloper = false, pageable = PAGEABLE)
        }.isInstanceOf(JobNotFoundException::class.java)
    }

    @Test
    fun `대상이 없으면 Member Port를 호출하지 않는다`() {
        givenJob(createdByMemberId = 100L)
        given(jobApplicationRepository.findApplicantsOf(JOB_ID, PAGEABLE)).willReturn(PageImpl(emptyList()))

        val result = service.list(JOB_ID, requesterMemberId = 100L, isDeveloper = false, pageable = PAGEABLE)

        assertThat(result.content).isEmpty()
        org.mockito.Mockito.verifyNoInteractions(applicationApplicantProfileQueryPort)
    }

    private fun givenApplications(vararg applications: JobApplication) {
        given(jobApplicationRepository.findApplicantsOf(JOB_ID, PAGEABLE)).willReturn(PageImpl(applications.toList()))
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
        applicantMemberId: Long,
    ) = JobApplication(
        jobId = JOB_ID,
        applicantMemberId = applicantMemberId,
        attemptNumber = 1,
        contactEmail = "student@example.com",
        answers = "[]",
        status = JobApplicationStatus.SUBMITTED,
    ).apply { this.id = id }

    private fun profileOf(
        memberId: Long,
        profileImageFileId: Long?,
    ) = ApplicationApplicantProfile(
        memberId = memberId,
        name = "홍길동",
        profileImageFileId = profileImageFileId,
        cohort = 10,
        department = "SW_DEVELOPMENT",
    )

    private companion object {
        const val JOB_ID = 1L
        const val OTHER_MEMBER_ID = 999L
        val PAGEABLE: PageRequest = PageRequest.of(0, 20)
    }
}
