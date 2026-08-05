package team.inreok.getiserver.domain.application.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.application.dto.JobApplicationFormLinkRequest
import team.inreok.getiserver.domain.application.entity.Form
import team.inreok.getiserver.domain.application.entity.JobApplicationForm
import team.inreok.getiserver.domain.application.entity.type.FormStatus
import team.inreok.getiserver.domain.application.entity.type.FormType
import team.inreok.getiserver.domain.application.exception.FormNotActiveException
import team.inreok.getiserver.domain.application.exception.FormNotFoundException
import team.inreok.getiserver.domain.application.exception.FormNotOwnedException
import team.inreok.getiserver.domain.application.exception.InvalidFormFieldException
import team.inreok.getiserver.domain.application.exception.JobApplicationMethodNotInternalException
import team.inreok.getiserver.domain.application.exception.JobManageForbiddenException
import team.inreok.getiserver.domain.application.exception.JobNotFoundException
import team.inreok.getiserver.domain.application.repository.FormRepository
import team.inreok.getiserver.domain.application.repository.JobApplicationFormRepository
import team.inreok.getiserver.domain.application.service.JobApplicationFormLinkService
import team.inreok.getiserver.domain.job.query.JobApplicationJobSnapshot
import team.inreok.getiserver.domain.job.query.JobApplicationSnapshotQueryPort
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class JobApplicationFormLinkServiceImplTest {
    @Mock
    private lateinit var jobApplicationSnapshotQueryPort: JobApplicationSnapshotQueryPort

    @Mock
    private lateinit var formRepository: FormRepository

    @Mock
    private lateinit var jobApplicationFormRepository: JobApplicationFormRepository

    private val service: JobApplicationFormLinkService by lazy {
        JobApplicationFormLinkServiceImpl(jobApplicationSnapshotQueryPort, formRepository, jobApplicationFormRepository)
    }

    private val fixedTime = LocalDateTime.of(2026, 3, 1, 10, 0, 0)

    private fun jobOf(
        applicationMethod: String = "INTERNAL",
        createdByMemberId: Long? = 100L,
        managerMemberId: Long? = null,
    ) = JobApplicationJobSnapshot(
        jobId = 1L,
        title = "인턴 채용",
        companyId = 1L,
        postingType = "MOU",
        applicationMethod = applicationMethod,
        status = "DRAFT",
        targetGrade = null,
        recruitmentStartedAt = null,
        recruitmentEndedAt = null,
        createdByMemberId = createdByMemberId,
        managerMemberId = managerMemberId,
    )

    private fun formOf(
        id: Long = 10L,
        ownerMemberId: Long = 100L,
        formType: FormType = FormType.JOB,
        status: FormStatus = FormStatus.ACTIVE,
    ) = Form(ownerMemberId = ownerMemberId, name = "지원서", formType = formType, status = status).apply {
        this.id = id
        this.updatedAt = fixedTime
    }

    private fun anyJobApplicationForm(): JobApplicationForm =
        any(JobApplicationForm::class.java) ?: JobApplicationForm(jobId = 0, formId = 0, linkedByMemberId = 0)

    @Test
    fun `공고 등록자가 본인 소유 ACTIVE 양식을 연결하면 성공한다`() {
        given(jobApplicationSnapshotQueryPort.findById(1L)).willReturn(jobOf(createdByMemberId = 100L))
        given(formRepository.findById(10L)).willReturn(Optional.of(formOf()))
        given(jobApplicationFormRepository.findById(1L)).willReturn(Optional.empty())
        given(jobApplicationFormRepository.saveAndFlush(anyJobApplicationForm())).willAnswer { invocation ->
            (invocation.arguments[0] as JobApplicationForm).apply { updatedAt = fixedTime }
        }

        val result = service.link(1L, 100L, isDeveloper = false, JobApplicationFormLinkRequest(formId = 10L))

        assertThat(result.jobId).isEqualTo(1L)
        assertThat(result.formId).isEqualTo(10L)
    }

    @Test
    fun `존재하지 않는 공고에 연결하면 JobNotFoundException을 던진다`() {
        given(jobApplicationSnapshotQueryPort.findById(999L)).willReturn(null)

        assertThatThrownBy {
            service.link(
                999L,
                100L,
                isDeveloper = false,
                JobApplicationFormLinkRequest(formId = 10L),
            )
        }.isInstanceOf(JobNotFoundException::class.java)
    }

    @Test
    fun `EXTERNAL 지원방식 공고에 연결하면 JobApplicationMethodNotInternalException을 던진다`() {
        given(jobApplicationSnapshotQueryPort.findById(1L)).willReturn(jobOf(applicationMethod = "EXTERNAL"))

        assertThatThrownBy { service.link(1L, 100L, isDeveloper = false, JobApplicationFormLinkRequest(formId = 10L)) }
            .isInstanceOf(JobApplicationMethodNotInternalException::class.java)
    }

    @Test
    fun `담당자가 아닌 교사가 연결하면 JobManageForbiddenException을 던진다`() {
        given(
            jobApplicationSnapshotQueryPort.findById(1L),
        ).willReturn(jobOf(createdByMemberId = 100L, managerMemberId = null))

        assertThatThrownBy { service.link(1L, 200L, isDeveloper = false, JobApplicationFormLinkRequest(formId = 10L)) }
            .isInstanceOf(JobManageForbiddenException::class.java)
    }

    @Test
    fun `개발자는 담당자가 아니어도 연결할 수 있다`() {
        given(jobApplicationSnapshotQueryPort.findById(1L)).willReturn(jobOf(createdByMemberId = 100L))
        given(formRepository.findById(10L)).willReturn(Optional.of(formOf(ownerMemberId = 200L)))
        given(jobApplicationFormRepository.findById(1L)).willReturn(Optional.empty())
        given(jobApplicationFormRepository.saveAndFlush(anyJobApplicationForm())).willAnswer { invocation ->
            (invocation.arguments[0] as JobApplicationForm).apply { updatedAt = fixedTime }
        }

        val result = service.link(1L, 200L, isDeveloper = true, JobApplicationFormLinkRequest(formId = 10L))

        assertThat(result.jobId).isEqualTo(1L)
    }

    @Test
    fun `개발자여도 본인 소유가 아닌 양식은 연결할 수 없다`() {
        given(jobApplicationSnapshotQueryPort.findById(1L)).willReturn(jobOf(createdByMemberId = 100L))
        given(formRepository.findById(10L)).willReturn(Optional.of(formOf(ownerMemberId = 999L)))

        assertThatThrownBy { service.link(1L, 200L, isDeveloper = true, JobApplicationFormLinkRequest(formId = 10L)) }
            .isInstanceOf(FormNotOwnedException::class.java)
    }

    @Test
    fun `존재하지 않는 양식을 연결하면 FormNotFoundException을 던진다`() {
        given(jobApplicationSnapshotQueryPort.findById(1L)).willReturn(jobOf())
        given(formRepository.findById(999L)).willReturn(Optional.empty())

        assertThatThrownBy { service.link(1L, 100L, isDeveloper = false, JobApplicationFormLinkRequest(formId = 999L)) }
            .isInstanceOf(FormNotFoundException::class.java)
    }

    @Test
    fun `PROGRAM 유형 양식을 연결하면 InvalidFormFieldException을 던진다`() {
        given(jobApplicationSnapshotQueryPort.findById(1L)).willReturn(jobOf())
        given(formRepository.findById(10L)).willReturn(Optional.of(formOf(formType = FormType.PROGRAM)))

        assertThatThrownBy { service.link(1L, 100L, isDeveloper = false, JobApplicationFormLinkRequest(formId = 10L)) }
            .isInstanceOf(InvalidFormFieldException::class.java)
    }

    @Test
    fun `ACTIVE가 아닌 양식을 연결하면 FormNotActiveException을 던진다`() {
        given(jobApplicationSnapshotQueryPort.findById(1L)).willReturn(jobOf())
        given(formRepository.findById(10L)).willReturn(Optional.of(formOf(status = FormStatus.DRAFT)))

        assertThatThrownBy { service.link(1L, 100L, isDeveloper = false, JobApplicationFormLinkRequest(formId = 10L)) }
            .isInstanceOf(FormNotActiveException::class.java)
    }

    @Test
    fun `이미 연결된 공고를 다시 연결하면 기존 Row를 갱신한다`() {
        val existing =
            JobApplicationForm(jobId = 1L, formId = 5L, linkedByMemberId = 100L).apply {
                updatedAt =
                    fixedTime
            }
        given(jobApplicationSnapshotQueryPort.findById(1L)).willReturn(jobOf())
        given(formRepository.findById(10L)).willReturn(Optional.of(formOf()))
        given(jobApplicationFormRepository.findById(1L)).willReturn(Optional.of(existing))
        given(jobApplicationFormRepository.saveAndFlush(anyJobApplicationForm())).willAnswer {
            it.arguments[0] as JobApplicationForm
        }

        service.link(1L, 100L, isDeveloper = false, JobApplicationFormLinkRequest(formId = 10L))

        assertThat(existing.formId).isEqualTo(10L)
    }
}
