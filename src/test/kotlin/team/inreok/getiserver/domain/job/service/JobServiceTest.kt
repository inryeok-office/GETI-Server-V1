package team.inreok.getiserver.domain.job.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.springframework.context.ApplicationEventPublisher
import team.inreok.getiserver.domain.company.query.CompanyQuery
import team.inreok.getiserver.domain.company.query.CompanySummary
import team.inreok.getiserver.domain.job.dto.JobCreateRequest
import team.inreok.getiserver.domain.job.dto.JobStatusUpdateRequest
import team.inreok.getiserver.domain.job.dto.JobUpdateRequest
import team.inreok.getiserver.domain.job.entity.Job
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.entity.type.PostingType
import team.inreok.getiserver.domain.job.event.JobDiscordAction
import team.inreok.getiserver.domain.job.event.JobDiscordEvent
import team.inreok.getiserver.domain.job.exception.JobCompanyNotFoundException
import team.inreok.getiserver.domain.job.exception.JobDiscordChannelNotAllowedException
import team.inreok.getiserver.domain.job.exception.JobFormRequiredException
import team.inreok.getiserver.domain.job.exception.JobNotFoundException
import team.inreok.getiserver.domain.job.exception.JobNotVisibleException
import team.inreok.getiserver.domain.job.exception.JobStatusTransitionInvalidException
import team.inreok.getiserver.domain.job.exception.JobValidationFailedException
import team.inreok.getiserver.domain.job.repository.JobRepository
import team.inreok.getiserver.domain.job.service.impl.JobServiceImpl
import team.inreok.getiserver.global.discord.DiscordChannelResolver
import java.time.LocalDateTime
import java.util.Optional

/**
 * Strictness를 LENIENT로 둔 이유는 공통 Fixture(`givenActiveCompany` 등)가 여러 Test에서
 * 재사용되는데, 검증 실패를 확인하는 Test는 Stub까지 도달하기 전에 예외를 던져 사용되지 않은
 * Stub이 남기 때문이다.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobServiceTest {
    @Mock
    private lateinit var jobRepository: JobRepository

    @Mock
    private lateinit var companyQuery: CompanyQuery

    @Mock
    private lateinit var eventPublisher: ApplicationEventPublisher

    @Mock
    private lateinit var discordChannelResolver: DiscordChannelResolver

    @Captor
    private lateinit var jobCaptor: ArgumentCaptor<Job>

    private val service: JobService by lazy {
        JobServiceImpl(jobRepository, companyQuery, eventPublisher, discordChannelResolver)
    }

    private val companySummary = CompanySummary(companyId = 1L, name = "인력개발원")
    private val companySummaryWithLogo = CompanySummary(companyId = 1L, name = "인력개발원", logoUrl = LOGO_URL)

    // --- 등록 ---

    @Test
    fun `DRAFT는 본문과 지원 URL이 없어도 임시저장된다`() {
        givenActiveCompany()
        givenSaveAssignsId()

        val response = service.create(draftRequest(), createdByMemberId = REQUESTER_ID)

        assertThat(response.status).isEqualTo(JobStatus.DRAFT)
        assertThat(response.content).isNull()
        assertThat(response.publishedAt).isNull()
        assertThat(response.company).isEqualTo(companySummary)
    }

    @Test
    fun `등록 응답의 company에 로고 URL이 담긴다`() {
        given(companyQuery.findActiveSummary(1L, REQUESTER_ID)).willReturn(companySummaryWithLogo)
        givenSaveAssignsId()

        val response = service.create(draftRequest(), createdByMemberId = REQUESTER_ID)

        assertThat(response.company?.logoUrl).isEqualTo(LOGO_URL)
    }

    @Test
    fun `등록 시 작성자를 Access Token에서 받은 회원 ID로 기록한다`() {
        givenActiveCompany()
        givenSaveAssignsId()

        service.create(draftRequest(), createdByMemberId = REQUESTER_ID)

        verify(jobRepository).saveAndFlush(jobCaptor.capture() ?: newJob())
        assertThat(jobCaptor.value.createdByMemberId).isEqualTo(REQUESTER_ID)
        // 담당자는 요청으로 받지 않으므로 항상 비어 있어야 한다(Issue #60 제외 범위).
        assertThat(jobCaptor.value.managerMemberId).isNull()
    }

    @Test
    fun `삭제되었거나 없는 기업으로 등록하면 COMPANY_NOT_FOUND로 거부한다`() {
        given(companyQuery.findActiveSummary(anyLong(), any())).willReturn(null)

        assertThatThrownBy { service.create(draftRequest(), REQUESTER_ID) }
            .isInstanceOf(JobCompanyNotFoundException::class.java)

        verify(jobRepository, never()).saveAndFlush(anyJob())
    }

    @Test
    fun `PUBLISHED로 등록하면 게시 시각을 기록한다`() {
        givenActiveCompany()
        givenSaveAssignsId()

        val response = service.create(publishableRequest(), REQUESTER_ID)

        assertThat(response.status).isEqualTo(JobStatus.PUBLISHED)
        assertThat(response.publishedAt).isNotNull()
    }

    @Test
    fun `PUBLISHED로 등록할 때 본문이 없으면 거부한다`() {
        givenActiveCompany()

        assertThatThrownBy { service.create(publishableRequest(content = null), REQUESTER_ID) }
            .isInstanceOf(JobValidationFailedException::class.java)
    }

    @Test
    fun `PUBLISHED로 등록할 때 외부 지원 URL이 없으면 거부한다`() {
        givenActiveCompany()

        assertThatThrownBy { service.create(publishableRequest(externalUrl = null), REQUESTER_ID) }
            .isInstanceOf(JobValidationFailedException::class.java)
    }

    @Test
    fun `INTERNAL 공고는 게시할 수 없고 JOB_FORM_REQUIRED로 거부한다`() {
        givenActiveCompany()

        assertThatThrownBy {
            service.create(publishableRequest(applicationMethod = ApplicationMethod.INTERNAL), REQUESTER_ID)
        }.isInstanceOf(JobFormRequiredException::class.java)
    }

    @Test
    fun `INTERNAL 공고도 DRAFT로는 저장된다`() {
        givenActiveCompany()
        givenSaveAssignsId()

        val response = service.create(draftRequest(applicationMethod = ApplicationMethod.INTERNAL), REQUESTER_ID)

        assertThat(response.applicationMethod).isEqualTo(ApplicationMethod.INTERNAL)
    }

    @Test
    fun `CLOSED나 DELETED 상태로는 등록할 수 없다`() {
        givenActiveCompany()

        assertThatThrownBy { service.create(draftRequest(status = JobStatus.CLOSED), REQUESTER_ID) }
            .isInstanceOf(JobValidationFailedException::class.java)
        verify(jobRepository, never()).saveAndFlush(anyJob())
    }

    @Test
    fun `제목이 공백만 있으면 거부한다`() {
        givenActiveCompany()

        assertThatThrownBy { service.create(draftRequest(title = "   "), REQUESTER_ID) }
            .isInstanceOf(JobValidationFailedException::class.java)
    }

    @Test
    fun `모집 시작 시각이 종료 시각보다 늦으면 거부한다`() {
        givenActiveCompany()

        assertThatThrownBy {
            service.create(
                draftRequest(
                    startDate = LocalDateTime.of(2026, 9, 1, 0, 0),
                    endDate = LocalDateTime.of(2026, 8, 1, 0, 0),
                ),
                REQUESTER_ID,
            )
        }.isInstanceOf(JobValidationFailedException::class.java)
    }

    @Test
    fun `대상 학년이 1에서 3 밖이면 거부한다`() {
        givenActiveCompany()

        assertThatThrownBy { service.create(draftRequest(targetGrade = 4), REQUESTER_ID) }
            .isInstanceOf(JobValidationFailedException::class.java)
    }

    @Test
    fun `모집 인원이 0 이하면 거부한다`() {
        givenActiveCompany()

        assertThatThrownBy { service.create(draftRequest(capacity = 0), REQUESTER_ID) }
            .isInstanceOf(JobValidationFailedException::class.java)
    }

    @Test
    fun `외부 지원 URL이 http나 https가 아니면 거부한다`() {
        givenActiveCompany()

        assertThatThrownBy { service.create(draftRequest(externalUrl = "ftp://example.com/apply"), REQUESTER_ID) }
            .isInstanceOf(JobValidationFailedException::class.java)
    }

    // --- 수정 ---

    @Test
    fun `전달한 Field만 수정하고 나머지는 유지한다`() {
        val job = jobOf(status = JobStatus.DRAFT, title = "원래 제목", capacity = 5)
        givenFoundNotDeleted(job)
        givenActiveCompany()

        service.update(1L, JobUpdateRequest(title = "새 제목"), REQUESTER_ID)

        assertThat(job.title).isEqualTo("새 제목")
        assertThat(job.capacity).isEqualTo(5)
    }

    @Test
    fun `수정 응답의 company에 로고 URL이 담긴다`() {
        val job = jobOf(status = JobStatus.DRAFT)
        givenFoundNotDeleted(job)
        given(companyQuery.findActiveSummary(1L, REQUESTER_ID)).willReturn(companySummaryWithLogo)

        val response = service.update(1L, JobUpdateRequest(title = "새 제목"), REQUESTER_ID)

        assertThat(response.company?.logoUrl).isEqualTo(LOGO_URL)
    }

    @Test
    fun `게시된 공고를 수정해 제목을 비우면 거부한다`() {
        val job = jobOf(status = JobStatus.PUBLISHED)
        givenFoundNotDeleted(job)

        assertThatThrownBy { service.update(1L, JobUpdateRequest(title = "   "), REQUESTER_ID) }
            .isInstanceOf(JobValidationFailedException::class.java)
    }

    @Test
    fun `없거나 삭제된 공고를 수정하면 JOB_NOT_FOUND로 거부한다`() {
        given(jobRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(null)

        assertThatThrownBy { service.update(1L, JobUpdateRequest(title = "새 제목"), REQUESTER_ID) }
            .isInstanceOf(JobNotFoundException::class.java)
    }

    // --- 상태 전이 ---

    @Test
    fun `DRAFT를 게시하면 게시 시각을 기록한다`() {
        val job = jobOf(status = JobStatus.DRAFT)
        givenFoundNotDeleted(job)
        givenActiveCompany()

        val response = service.changeStatus(1L, JobStatusUpdateRequest(JobStatus.PUBLISHED), REQUESTER_ID)

        assertThat(response.status).isEqualTo(JobStatus.PUBLISHED)
        assertThat(job.publishedAt).isNotNull()
    }

    @Test
    fun `게시 필수값을 갖추지 못한 DRAFT는 게시할 수 없다`() {
        val job = jobOf(status = JobStatus.DRAFT, content = null)
        givenFoundNotDeleted(job)

        assertThatThrownBy { service.changeStatus(1L, JobStatusUpdateRequest(JobStatus.PUBLISHED), REQUESTER_ID) }
            .isInstanceOf(JobValidationFailedException::class.java)
        assertThat(job.status).isEqualTo(JobStatus.DRAFT)
    }

    @Test
    fun `INTERNAL 공고는 상태 변경으로도 게시할 수 없다`() {
        val job = jobOf(status = JobStatus.DRAFT, applicationMethod = ApplicationMethod.INTERNAL)
        givenFoundNotDeleted(job)

        assertThatThrownBy { service.changeStatus(1L, JobStatusUpdateRequest(JobStatus.PUBLISHED), REQUESTER_ID) }
            .isInstanceOf(JobFormRequiredException::class.java)
    }

    @Test
    fun `게시된 공고를 마감하면 마감 시각을 기록한다`() {
        val job = jobOf(status = JobStatus.PUBLISHED)
        givenFoundNotDeleted(job)
        givenActiveCompany()

        service.changeStatus(1L, JobStatusUpdateRequest(JobStatus.CLOSED), REQUESTER_ID)

        assertThat(job.status).isEqualTo(JobStatus.CLOSED)
        assertThat(job.closedAt).isNotNull()
    }

    @Test
    fun `삭제는 Soft Delete로 상태와 삭제 시각을 함께 기록한다`() {
        val job = jobOf(status = JobStatus.PUBLISHED)
        givenFoundNotDeleted(job)
        givenActiveCompany()

        service.changeStatus(1L, JobStatusUpdateRequest(JobStatus.DELETED), REQUESTER_ID)

        assertThat(job.status).isEqualTo(JobStatus.DELETED)
        assertThat(job.deletedAt).isNotNull()
        // 실제 행을 지우지 않아야 북마크와 지원 이력이 보존된다.
        verify(jobRepository, never()).delete(anyJob())
    }

    @Test
    fun `마감된 공고를 다시 게시할 수 없다`() {
        givenFoundNotDeleted(jobOf(status = JobStatus.CLOSED))

        assertThatThrownBy { service.changeStatus(1L, JobStatusUpdateRequest(JobStatus.PUBLISHED), REQUESTER_ID) }
            .isInstanceOf(JobStatusTransitionInvalidException::class.java)
    }

    @Test
    fun `같은 상태로의 변경도 거부한다`() {
        givenFoundNotDeleted(jobOf(status = JobStatus.PUBLISHED))

        assertThatThrownBy { service.changeStatus(1L, JobStatusUpdateRequest(JobStatus.PUBLISHED), REQUESTER_ID) }
            .isInstanceOf(JobStatusTransitionInvalidException::class.java)
    }

    @Test
    fun `이미 삭제된 공고는 상태를 바꿀 수 없다`() {
        given(jobRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(null)

        assertThatThrownBy { service.changeStatus(1L, JobStatusUpdateRequest(JobStatus.PUBLISHED), REQUESTER_ID) }
            .isInstanceOf(JobNotFoundException::class.java)
    }

    // --- 공개 상세와 조회수 ---

    @Test
    fun `공개 상세를 조회하면 조회수를 원자적으로 증가시키고 증가된 값을 응답한다`() {
        val job = jobOf(status = JobStatus.PUBLISHED, viewCount = 10)
        givenFoundNotDeleted(job)
        givenActiveCompany()

        val response = service.getPublicDetail(1L, REQUESTER_ID)

        assertThat(response.viewCount).isEqualTo(11)
        verify(jobRepository).incrementViewCount(1L)
    }

    @Test
    fun `공개 상세 응답의 company에 로고 URL이 담긴다`() {
        val job = jobOf(status = JobStatus.PUBLISHED)
        givenFoundNotDeleted(job)
        given(companyQuery.findActiveSummary(1L, REQUESTER_ID)).willReturn(companySummaryWithLogo)

        val response = service.getPublicDetail(1L, REQUESTER_ID)

        assertThat(response.company?.logoUrl).isEqualTo(LOGO_URL)
    }

    @Test
    fun `기업에 로고가 없으면 응답의 logoUrl은 null이다`() {
        val job = jobOf(status = JobStatus.PUBLISHED)
        givenFoundNotDeleted(job)
        givenActiveCompany()

        val response = service.getPublicDetail(1L, REQUESTER_ID)

        assertThat(response.company?.logoUrl).isNull()
    }

    @Test
    fun `임시저장 공고를 공개 상세로 조회하면 JOB_NOT_VISIBLE로 거부한다`() {
        givenFoundNotDeleted(jobOf(status = JobStatus.DRAFT))

        assertThatThrownBy { service.getPublicDetail(1L, REQUESTER_ID) }
            .isInstanceOf(JobNotVisibleException::class.java)
        verify(jobRepository, never()).incrementViewCount(anyLong())
    }

    @Test
    fun `마감된 공고는 공개 상세로 조회할 수 있다`() {
        givenFoundNotDeleted(jobOf(status = JobStatus.CLOSED))
        givenActiveCompany()

        val response = service.getPublicDetail(1L, REQUESTER_ID)

        assertThat(response.status).isEqualTo(JobStatus.CLOSED)
    }

    @Test
    fun `삭제된 공고는 공개 상세에서 JOB_NOT_FOUND로 처리한다`() {
        given(jobRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(null)

        assertThatThrownBy { service.getPublicDetail(1L, REQUESTER_ID) }
            .isInstanceOf(JobNotFoundException::class.java)
    }

    @Test
    fun `관리자 상세는 모든 상태를 조회하고 조회수를 올리지 않는다`() {
        val job = jobOf(status = JobStatus.DRAFT, viewCount = 10)
        given(jobRepository.findById(1L)).willReturn(Optional.of(job))
        givenActiveCompany()

        val response = service.getForAdmin(1L, REQUESTER_ID)

        assertThat(response.status).isEqualTo(JobStatus.DRAFT)
        assertThat(response.viewCount).isEqualTo(10)
        verify(jobRepository, never()).incrementViewCount(anyLong())
    }

    @Test
    fun `관리자 상세 응답의 company에 로고 URL이 담긴다`() {
        val job = jobOf(status = JobStatus.DRAFT)
        given(jobRepository.findById(1L)).willReturn(Optional.of(job))
        given(companyQuery.findActiveSummary(1L, REQUESTER_ID)).willReturn(companySummaryWithLogo)

        val response = service.getForAdmin(1L, REQUESTER_ID)

        assertThat(response.company?.logoUrl).isEqualTo(LOGO_URL)
    }

    // --- Discord Event 발행 (docs/notification/discord-event-wiring-plan.md §4.2) ---
    //
    // 한 번도 게시되지 않은 DRAFT는 Discord에 메시지가 없어, UPDATED/DELETED를 발행하면 Worker가
    // MISSING_DISCORD_MESSAGE_ID로 실패 처리할 Row만 쌓인다. 그래서 발행 단계에서 거른다.

    @Test
    fun `PUBLISHED로 등록하면 Discord PUBLISHED Event를 발행한다`() {
        givenActiveCompany()
        givenSaveAssignsId()

        service.create(publishableRequest(), REQUESTER_ID)

        assertThat(publishedDiscordEvents()).containsExactly(JobDiscordEvent(1L, JobDiscordAction.PUBLISHED))
    }

    @Test
    fun `DRAFT로 등록하면 Discord Event를 발행하지 않는다`() {
        givenActiveCompany()
        givenSaveAssignsId()

        service.create(draftRequest(), REQUESTER_ID)

        assertThat(publishedDiscordEvents()).isEmpty()
    }

    @Test
    fun `게시된 공고를 수정하면 Discord UPDATED Event를 발행한다`() {
        givenFoundNotDeleted(jobOf(status = JobStatus.PUBLISHED))
        givenActiveCompany()

        service.update(1L, JobUpdateRequest(title = "수정된 제목"), REQUESTER_ID)

        assertThat(publishedDiscordEvents()).containsExactly(JobDiscordEvent(1L, JobDiscordAction.UPDATED))
    }

    @Test
    fun `DRAFT를 수정하면 Discord Event를 발행하지 않는다`() {
        givenFoundNotDeleted(jobOf(status = JobStatus.DRAFT))
        givenActiveCompany()

        service.update(1L, JobUpdateRequest(title = "수정된 제목"), REQUESTER_ID)

        assertThat(publishedDiscordEvents()).isEmpty()
    }

    @Test
    fun `게시된 공고를 마감하면 Discord CLOSED Event를 발행한다`() {
        givenFoundNotDeleted(jobOf(status = JobStatus.PUBLISHED))
        givenActiveCompany()

        service.changeStatus(1L, JobStatusUpdateRequest(JobStatus.CLOSED), REQUESTER_ID)

        assertThat(publishedDiscordEvents()).containsExactly(JobDiscordEvent(1L, JobDiscordAction.CLOSED))
    }

    @Test
    fun `게시된 공고를 삭제하면 Discord DELETED Event를 발행한다`() {
        givenFoundNotDeleted(jobOf(status = JobStatus.PUBLISHED))
        givenActiveCompany()

        service.changeStatus(1L, JobStatusUpdateRequest(JobStatus.DELETED), REQUESTER_ID)

        assertThat(publishedDiscordEvents()).containsExactly(JobDiscordEvent(1L, JobDiscordAction.DELETED))
    }

    @Test
    fun `DRAFT를 삭제하면 Discord Event를 발행하지 않는다`() {
        givenFoundNotDeleted(jobOf(status = JobStatus.DRAFT))
        givenActiveCompany()

        service.changeStatus(1L, JobStatusUpdateRequest(JobStatus.DELETED), REQUESTER_ID)

        assertThat(publishedDiscordEvents()).isEmpty()
    }

    @Test
    fun `DRAFT를 게시하면 Discord PUBLISHED Event를 발행한다`() {
        givenFoundNotDeleted(jobOf(status = JobStatus.DRAFT))
        givenActiveCompany()

        service.changeStatus(1L, JobStatusUpdateRequest(JobStatus.PUBLISHED), REQUESTER_ID)

        assertThat(publishedDiscordEvents()).containsExactly(JobDiscordEvent(1L, JobDiscordAction.PUBLISHED))
    }

    @Test
    fun `허용 목록에 없는 Discord 채널 Key로 등록하면 거부한다`() {
        givenActiveCompany()
        given(discordChannelResolver.isAllowedJobChannelKey("random-channel")).willReturn(false)

        assertThatThrownBy {
            service.create(publishableRequest().copy(discordChannelKey = "random-channel"), REQUESTER_ID)
        }.isInstanceOf(JobDiscordChannelNotAllowedException::class.java)

        verify(jobRepository, never()).saveAndFlush(anyJob())
    }

    // --- Fixture ---
    //
    // Kotlin non-null 파라미터에 bare any()를 쓰면 null이 반환되어 NPE가 나므로 Elvis로 기본값을
    // 준다(CompanyServiceTest.anyCompany와 같은 이유).

    /**
     * 발행된 Event 중 [JobDiscordEvent]만 골라낸다. 같은 지점에서 `JobChangedEvent`(search 색인
     * 동기화)도 함께 발행되므로 Event 종류를 구분하지 않으면 검증이 흐려진다.
     */
    private fun publishedDiscordEvents(): List<JobDiscordEvent> {
        val captor = ArgumentCaptor.forClass(Any::class.java)
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture() ?: Any())
        return captor.allValues.filterIsInstance<JobDiscordEvent>()
    }

    private fun anyJob(): Job = any(Job::class.java) ?: newJob()

    private fun givenActiveCompany() {
        given(companyQuery.findActiveSummary(1L, REQUESTER_ID)).willReturn(companySummary)
    }

    /** 실제 저장처럼 id를 채워 준다. id가 없으면 응답 DTO가 만들어지지 않는다. */
    private fun givenSaveAssignsId() {
        given(jobRepository.saveAndFlush(anyJob())).willAnswer { invocation ->
            (invocation.arguments[0] as Job).apply { id = 1L }
        }
    }

    private fun givenFoundNotDeleted(job: Job) {
        given(jobRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(job)
    }

    private fun draftRequest(
        title: String = "2026 상반기 백엔드 채용",
        status: JobStatus = JobStatus.DRAFT,
        applicationMethod: ApplicationMethod = ApplicationMethod.EXTERNAL,
        content: String? = null,
        externalUrl: String? = null,
        startDate: LocalDateTime? = null,
        endDate: LocalDateTime? = null,
        targetGrade: Int? = null,
        capacity: Int? = null,
    ) = JobCreateRequest(
        companyId = 1L,
        postingType = PostingType.MOU,
        applicationMethod = applicationMethod,
        title = title,
        status = status,
        content = content,
        externalUrl = externalUrl,
        startDate = startDate,
        endDate = endDate,
        targetGrade = targetGrade,
        capacity = capacity,
    )

    private fun publishableRequest(
        applicationMethod: ApplicationMethod = ApplicationMethod.EXTERNAL,
        content: String? = "## 모집 부문",
        externalUrl: String? = "https://example.com/apply",
    ) = draftRequest(
        status = JobStatus.PUBLISHED,
        applicationMethod = applicationMethod,
        content = content,
        externalUrl = externalUrl,
    )

    private fun newJob() = Job(1L, PostingType.MOU, ApplicationMethod.EXTERNAL, "제목")

    private fun jobOf(
        id: Long = 1L,
        status: JobStatus = JobStatus.PUBLISHED,
        title: String = "2026 상반기 백엔드 채용",
        applicationMethod: ApplicationMethod = ApplicationMethod.EXTERNAL,
        content: String? = "## 모집 부문",
        externalUrl: String? = "https://example.com/apply",
        capacity: Int? = null,
        viewCount: Long = 0,
    ) = Job(
        companyId = 1L,
        type = PostingType.MOU,
        applicationMethod = applicationMethod,
        title = title,
        status = status,
        viewCount = viewCount,
    ).apply {
        this.id = id
        bodyMarkdown = content
        this.externalUrl = externalUrl
        this.capacity = capacity
    }

    private companion object {
        private const val REQUESTER_ID = 7L
        private const val LOGO_URL = "https://storage.example/company-logo?signature=test"
    }
}
