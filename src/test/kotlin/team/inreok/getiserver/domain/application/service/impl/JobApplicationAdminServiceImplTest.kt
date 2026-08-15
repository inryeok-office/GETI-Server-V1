package team.inreok.getiserver.domain.application.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import team.inreok.getiserver.domain.application.dto.JobApplicationAdminAction
import team.inreok.getiserver.domain.application.dto.JobApplicationAdminActionRequest
import team.inreok.getiserver.domain.application.entity.JobApplication
import team.inreok.getiserver.domain.application.entity.JobApplicationStatusHistory
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus
import team.inreok.getiserver.domain.application.exception.ApplicationActionNotAvailableException
import team.inreok.getiserver.domain.application.exception.ApplicationNotFoundException
import team.inreok.getiserver.domain.application.exception.ApplicationReviewForbiddenException
import team.inreok.getiserver.domain.application.repository.JobApplicationRepository
import team.inreok.getiserver.domain.application.repository.JobApplicationStatusHistoryRepository
import team.inreok.getiserver.domain.application.service.JobApplicationAdminService
import team.inreok.getiserver.domain.file.link.FileLinkPort
import team.inreok.getiserver.domain.job.query.JobApplicationJobSnapshot
import team.inreok.getiserver.domain.job.query.JobApplicationSnapshotQueryPort
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class JobApplicationAdminServiceImplTest {
    @Mock
    private lateinit var jobApplicationRepository: JobApplicationRepository

    @Mock
    private lateinit var jobApplicationSnapshotQueryPort: JobApplicationSnapshotQueryPort

    @Mock
    private lateinit var jobApplicationStatusHistoryRepository: JobApplicationStatusHistoryRepository

    @Mock
    private lateinit var fileLinkPort: FileLinkPort

    private val service: JobApplicationAdminService by lazy {
        JobApplicationAdminServiceImpl(
            jobApplicationRepository,
            jobApplicationSnapshotQueryPort,
            jobApplicationStatusHistoryRepository,
            fileLinkPort,
            JsonMapper(),
        )
    }

    private val fixedTime = LocalDateTime.of(2026, 3, 1, 10, 0, 0)

    private fun applicationOf(
        id: Long = 1L,
        status: JobApplicationStatus = JobApplicationStatus.SUBMITTED,
    ) = JobApplication(
        jobId = 1L,
        applicantMemberId = 1L,
        attemptNumber = 1,
        contactEmail = "student@example.com",
        answers = "[]",
        status = status,
    ).apply {
        this.id = id
        createdAt = fixedTime
        updatedAt = fixedTime
    }

    private fun jobOf(
        createdByMemberId: Long? = 100L,
        managerMemberId: Long? = null,
    ) = JobApplicationJobSnapshot(
        jobId = 1L,
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
    )

    // ---------- list ----------

    @Test
    fun `목록을 조회하면 항목을 반환한다`() {
        val page = PageImpl(listOf(applicationOf()), PageRequest.of(0, 20), 1)
        given(jobApplicationRepository.search(null, null, PageRequest.of(0, 20))).willReturn(page)

        val result = service.list(null, null, PageRequest.of(0, 20))

        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].applicationId).isEqualTo(1L)
        assertThat(result.totalElements).isEqualTo(1)
    }

    // ---------- getDetail ----------

    @Test
    fun `상세 조회 시 지원서가 없으면 ApplicationNotFoundException을 던진다`() {
        given(jobApplicationRepository.findById(999L)).willReturn(Optional.empty())

        assertThatThrownBy { service.getDetail(999L) }.isInstanceOf(ApplicationNotFoundException::class.java)
    }

    @Test
    fun `상세 조회 시 지원서가 있으면 결과를 반환한다`() {
        given(jobApplicationRepository.findById(1L)).willReturn(Optional.of(applicationOf()))

        val result = service.getDetail(1L)

        assertThat(result.applicationId).isEqualTo(1L)
        assertThat(result.status).isEqualTo(JobApplicationStatus.SUBMITTED)
    }

    @Test
    fun `상세 조회 시 DRAFT 상태면 ApplicationNotFoundException을 던진다`() {
        given(
            jobApplicationRepository.findById(1L),
        ).willReturn(Optional.of(applicationOf(status = JobApplicationStatus.DRAFT)))

        assertThatThrownBy { service.getDetail(1L) }.isInstanceOf(ApplicationNotFoundException::class.java)
    }

    // ---------- executeAction ----------

    @Test
    fun `담당 교사가 REQUEST_REVISION하면 REVISION_REQUESTED로 전이하고 사유를 기록한다`() {
        given(
            jobApplicationRepository.findByIdForUpdate(1L),
        ).willReturn(applicationOf(status = JobApplicationStatus.SUBMITTED))
        given(jobApplicationSnapshotQueryPort.findById(1L)).willReturn(jobOf(createdByMemberId = 100L))

        val result =
            service.executeAction(
                1L,
                100L,
                isDeveloper = false,
                JobApplicationAdminActionRequest(JobApplicationAdminAction.REQUEST_REVISION, reason = "보완 필요"),
            )

        assertThat(result.status).isEqualTo(JobApplicationStatus.REVISION_REQUESTED)
        assertThat(result.statusReason).isEqualTo("보완 필요")
    }

    @Test
    fun `교사 Action이 성공하면 상태 이력을 사유와 함께 기록한다`() {
        given(
            jobApplicationRepository.findByIdForUpdate(1L),
        ).willReturn(applicationOf(status = JobApplicationStatus.SUBMITTED))
        given(jobApplicationSnapshotQueryPort.findById(1L)).willReturn(jobOf(createdByMemberId = 100L))

        service.executeAction(
            1L,
            100L,
            isDeveloper = false,
            JobApplicationAdminActionRequest(JobApplicationAdminAction.REQUEST_REVISION, reason = "보완 필요"),
        )

        val historyCaptor = ArgumentCaptor.forClass(JobApplicationStatusHistory::class.java)
        verify(jobApplicationStatusHistoryRepository).save(historyCaptor.capture())
        assertThat(historyCaptor.value.fromStatus).isEqualTo(JobApplicationStatus.SUBMITTED)
        assertThat(historyCaptor.value.toStatus).isEqualTo(JobApplicationStatus.REVISION_REQUESTED)
        assertThat(historyCaptor.value.action).isEqualTo("REQUEST_REVISION")
        assertThat(historyCaptor.value.actorMemberId).isEqualTo(100L)
        assertThat(historyCaptor.value.reason).isEqualTo("보완 필요")
    }

    @Test
    fun `담당 교사가 APPROVE하면 APPROVED로 전이한다`() {
        given(
            jobApplicationRepository.findByIdForUpdate(1L),
        ).willReturn(applicationOf(status = JobApplicationStatus.SUBMITTED))
        given(jobApplicationSnapshotQueryPort.findById(1L)).willReturn(jobOf(createdByMemberId = 100L))

        val result =
            service.executeAction(
                1L,
                100L,
                isDeveloper = false,
                JobApplicationAdminActionRequest(JobApplicationAdminAction.APPROVE),
            )

        assertThat(result.status).isEqualTo(JobApplicationStatus.APPROVED)
    }

    @Test
    fun `담당 교사가 EDIT_REQUESTED 지원서를 ALLOW_EDIT하면 EDIT_ALLOWED로 전이한다`() {
        given(
            jobApplicationRepository.findByIdForUpdate(1L),
        ).willReturn(applicationOf(status = JobApplicationStatus.EDIT_REQUESTED))
        given(jobApplicationSnapshotQueryPort.findById(1L)).willReturn(jobOf(createdByMemberId = 100L))

        val result =
            service.executeAction(
                1L,
                100L,
                isDeveloper = false,
                JobApplicationAdminActionRequest(JobApplicationAdminAction.ALLOW_EDIT),
            )

        assertThat(result.status).isEqualTo(JobApplicationStatus.EDIT_ALLOWED)
    }

    @Test
    fun `허용되지 않은 상태에서 Action을 수행하면 ApplicationActionNotAvailableException을 던진다`() {
        given(
            jobApplicationRepository.findByIdForUpdate(1L),
        ).willReturn(applicationOf(status = JobApplicationStatus.DRAFT))
        given(jobApplicationSnapshotQueryPort.findById(1L)).willReturn(jobOf(createdByMemberId = 100L))

        assertThatThrownBy {
            service.executeAction(
                1L,
                100L,
                isDeveloper = false,
                JobApplicationAdminActionRequest(JobApplicationAdminAction.APPROVE),
            )
        }.isInstanceOf(ApplicationActionNotAvailableException::class.java)
    }

    @Test
    fun `최종 상태(APPROVED)에서 재전이를 시도하면 ApplicationActionNotAvailableException을 던진다`() {
        given(
            jobApplicationRepository.findByIdForUpdate(1L),
        ).willReturn(applicationOf(status = JobApplicationStatus.APPROVED))
        given(jobApplicationSnapshotQueryPort.findById(1L)).willReturn(jobOf(createdByMemberId = 100L))

        assertThatThrownBy {
            service.executeAction(
                1L,
                100L,
                isDeveloper = false,
                JobApplicationAdminActionRequest(JobApplicationAdminAction.REJECT),
            )
        }.isInstanceOf(ApplicationActionNotAvailableException::class.java)
    }

    @Test
    fun `담당자가 아닌 교사가 Action을 수행하면 ApplicationReviewForbiddenException을 던진다`() {
        given(
            jobApplicationRepository.findByIdForUpdate(1L),
        ).willReturn(applicationOf(status = JobApplicationStatus.SUBMITTED))
        given(
            jobApplicationSnapshotQueryPort.findById(1L),
        ).willReturn(jobOf(createdByMemberId = 100L, managerMemberId = null))

        assertThatThrownBy {
            service.executeAction(
                1L,
                200L,
                isDeveloper = false,
                JobApplicationAdminActionRequest(JobApplicationAdminAction.APPROVE),
            )
        }.isInstanceOf(ApplicationReviewForbiddenException::class.java)
    }

    @Test
    fun `개발자는 담당자가 아니어도 Action을 수행할 수 있다`() {
        given(
            jobApplicationRepository.findByIdForUpdate(1L),
        ).willReturn(applicationOf(status = JobApplicationStatus.SUBMITTED))

        val result =
            service.executeAction(
                1L,
                999L,
                isDeveloper = true,
                JobApplicationAdminActionRequest(JobApplicationAdminAction.APPROVE),
            )

        assertThat(result.status).isEqualTo(JobApplicationStatus.APPROVED)
    }

    @Test
    fun `공고 Snapshot을 찾을 수 없으면 개발자가 아닌 한 ApplicationReviewForbiddenException을 던진다`() {
        given(
            jobApplicationRepository.findByIdForUpdate(1L),
        ).willReturn(applicationOf(status = JobApplicationStatus.SUBMITTED))
        given(jobApplicationSnapshotQueryPort.findById(1L)).willReturn(null)

        assertThatThrownBy {
            service.executeAction(
                1L,
                100L,
                isDeveloper = false,
                JobApplicationAdminActionRequest(JobApplicationAdminAction.APPROVE),
            )
        }.isInstanceOf(ApplicationReviewForbiddenException::class.java)
    }

    @Test
    fun `존재하지 않는 지원서에 Action을 수행하면 ApplicationNotFoundException을 던진다`() {
        given(jobApplicationRepository.findByIdForUpdate(999L)).willReturn(null)

        assertThatThrownBy {
            service.executeAction(
                999L,
                100L,
                isDeveloper = false,
                JobApplicationAdminActionRequest(JobApplicationAdminAction.APPROVE),
            )
        }.isInstanceOf(ApplicationNotFoundException::class.java)
    }

    // ---------- getHistory ----------

    @Test
    fun `지원서 상태 이력을 오래된 순으로 반환한다`() {
        given(jobApplicationRepository.findById(1L)).willReturn(Optional.of(applicationOf()))
        val history =
            JobApplicationStatusHistory(
                applicationId = 1L,
                fromStatus = JobApplicationStatus.SUBMITTED,
                toStatus = JobApplicationStatus.APPROVED,
                action = "APPROVE",
                actorMemberId = 100L,
                reason = null,
            ).apply {
                id = 1L
                createdAt = fixedTime
            }
        given(jobApplicationStatusHistoryRepository.findByApplicationIdOrderByCreatedAtAsc(1L))
            .willReturn(listOf(history))

        val result = service.getHistory(1L)

        assertThat(result).hasSize(1)
        assertThat(result[0].toStatus).isEqualTo(JobApplicationStatus.APPROVED)
        assertThat(result[0].action).isEqualTo("APPROVE")
        assertThat(result[0].actorMemberId).isEqualTo(100L)
    }

    @Test
    fun `DRAFT 지원서의 이력을 조회하면 ApplicationNotFoundException을 던진다`() {
        given(
            jobApplicationRepository.findById(1L),
        ).willReturn(Optional.of(applicationOf(status = JobApplicationStatus.DRAFT)))

        assertThatThrownBy { service.getHistory(1L) }.isInstanceOf(ApplicationNotFoundException::class.java)
    }
}
