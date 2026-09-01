package team.inreok.getiserver.domain.job.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.springframework.context.ApplicationEventPublisher
import team.inreok.getiserver.domain.company.query.CompanyQuery
import team.inreok.getiserver.domain.company.query.CompanySummary
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.exception.FileNotOwnedException
import team.inreok.getiserver.domain.file.link.FileLinkPort
import team.inreok.getiserver.domain.file.link.FileSnapshot
import team.inreok.getiserver.domain.job.access.JobAiAnalysisAccessor
import team.inreok.getiserver.domain.job.access.JobApplicationEligibilityAccessSnapshot
import team.inreok.getiserver.domain.job.access.JobApplicationEligibilityAccessor
import team.inreok.getiserver.domain.job.access.JobBookmarkAccessor
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
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.query.MemberRoleQueryPort
import team.inreok.getiserver.global.discord.DiscordChannelResolver
import java.time.LocalDateTime
import java.util.Optional

/**
 * Strictness를 LENIENT로 둔 이유는 공통 Fixture(`givenActiveCompany` 등)가 여러 Test에서
 * 재사용되는데, 검증 실패를 확인하는 Test는 Stub까지 도달하기 전에 예외를 던져 사용되지 않은
 * Stub이 남기 때문이다.
 *
 * Job CRUD·상태 전이·File 연동(Issue #126)을 한 Class가 모두 검증해 detekt 기본 LargeClass
 * 임계값을 넘는다. `ProgramServiceImplTest`가 이미 같은 이유로 Suppress한 전례를 따른다.
 */
@Suppress("LargeClass")
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

    @Mock
    private lateinit var jobAiAnalysisAccessor: JobAiAnalysisAccessor

    @Mock
    private lateinit var jobApplicationEligibilityAccessor: JobApplicationEligibilityAccessor

    @Mock
    private lateinit var jobBookmarkAccessor: JobBookmarkAccessor

    @Mock
    private lateinit var fileLinkPort: FileLinkPort

    @Mock
    private lateinit var memberRoleQueryPort: MemberRoleQueryPort

    @Captor
    private lateinit var jobCaptor: ArgumentCaptor<Job>

    private val service: JobService by lazy {
        // 이 Test의 관심사는 지원 가능 여부 계산(Issue #136, application.access 구현체)이나 북마크
        // 여부 계산(Issue #171, recommendation.access 구현체)이 아니라 Job 자체의 CRUD/상태
        // 전이이므로, 요청된 jobId 전체에 기본값(canApply=true, bookmarked=false)을 채워주는 Stub
        // 하나로 모든 Test가 공유한다. 다른 값을 검증하려는 개별 Test는 이 Mock을 별도로
        // 재정의(given)하면 된다.
        given(
            jobApplicationEligibilityAccessor.findAllByJobIds(any() ?: emptySet(), anyLong()),
        ).willAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val jobIds = invocation.arguments[0] as Set<Long>
            jobIds.associateWith { defaultApplicationEligibility() }
        }
        given(jobBookmarkAccessor.findAllByJobIds(any() ?: emptySet(), anyLong())).willReturn(emptySet())
        JobServiceImpl(
            jobRepository,
            companyQuery,
            eventPublisher,
            discordChannelResolver,
            jobAiAnalysisAccessor,
            jobApplicationEligibilityAccessor,
            jobBookmarkAccessor,
            fileLinkPort,
            memberRoleQueryPort,
        )
    }

    private fun defaultApplicationEligibility() =
        JobApplicationEligibilityAccessSnapshot(
            canApply = true,
            eligibilityReason = "AVAILABLE",
            eligibilityMessage = "지원 가능한 공고입니다.",
            applicationId = null,
            applicationStatus = null,
            availableActions = listOf("CREATE_DRAFT"),
        )

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
    fun `등록 요청의 근무지역과 고용형태를 그대로 저장하고 응답한다`() {
        givenActiveCompany()
        givenSaveAssignsId()

        val response =
            service.create(
                draftRequest(location = "서울특별시 중구", employmentType = "인턴"),
                createdByMemberId = REQUESTER_ID,
            )

        verify(jobRepository).saveAndFlush(jobCaptor.capture() ?: newJob())
        assertThat(jobCaptor.value.location).isEqualTo("서울특별시 중구")
        assertThat(jobCaptor.value.employmentType).isEqualTo("인턴")
        assertThat(response.location).isEqualTo("서울특별시 중구")
        assertThat(response.employmentType).isEqualTo("인턴")
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
    fun `근무지역만 수정하면 고용형태는 기존 값을 유지한다`() {
        val job =
            jobOf(status = JobStatus.DRAFT).apply {
                location = "서울특별시 중구"
                employmentType = "인턴"
            }
        givenFoundNotDeleted(job)
        givenActiveCompany()

        service.update(1L, JobUpdateRequest(location = "부산광역시 해운대구"), REQUESTER_ID)

        assertThat(job.location).isEqualTo("부산광역시 해운대구")
        assertThat(job.employmentType).isEqualTo("인턴")
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
        // Storage Binary는 지우지 않고 연결만 해제한다(FileLinkPort.unlinkAllOf KDoc 참고).
        verify(fileLinkPort).unlinkAllOf(FileOwnerType.JOB, 1L)
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

    // --- 첨부파일 ---

    @Test
    fun `등록 시 fileIds가 있으면 저장된 jobId로 FileLinkPort를 호출한다`() {
        givenActiveCompany()
        givenSaveAssignsId()

        service.create(draftRequest(fileIds = listOf(1L, 2L)), createdByMemberId = REQUESTER_ID)

        verify(fileLinkPort).validateAndLink(
            requesterId = REQUESTER_ID,
            fileIds = listOf(1L, 2L),
            purpose = FilePurpose.JOB_ATTACHMENT,
            ownerId = 1L,
        )
    }

    @Test
    fun `등록 시 fileIds가 비어 있으면 FileLinkPort를 호출하지 않는다`() {
        givenActiveCompany()
        givenSaveAssignsId()

        service.create(draftRequest(), createdByMemberId = REQUESTER_ID)

        verify(fileLinkPort, never()).validateAndLink(anyLong(), anyList(), anyPurpose(), anyLong())
    }

    @Test
    fun `등록 시 타인이 업로드한 파일이면 FILE_NOT_OWNED로 등록이 실패한다`() {
        givenActiveCompany()
        givenSaveAssignsId()
        given(
            fileLinkPort.validateAndLink(
                requesterId = REQUESTER_ID,
                fileIds = listOf(1L),
                purpose = FilePurpose.JOB_ATTACHMENT,
                ownerId = 1L,
            ),
        ).willThrow(FileNotOwnedException(1L))

        assertThatThrownBy { service.create(draftRequest(fileIds = listOf(1L)), createdByMemberId = REQUESTER_ID) }
            .isInstanceOf(FileNotOwnedException::class.java)
    }

    @Test
    fun `수정 시 fileIds를 전달하지 않으면 기존 첨부파일을 유지한다`() {
        givenFoundNotDeleted(jobOf(status = JobStatus.DRAFT))
        givenActiveCompany()

        service.update(1L, JobUpdateRequest(title = "새 제목"), REQUESTER_ID)

        verify(fileLinkPort, never()).unlinkAllOf(FileOwnerType.JOB, 1L)
        verify(fileLinkPort, never()).validateAndLink(anyLong(), anyList(), anyPurpose(), anyLong())
    }

    @Test
    fun `수정 시 fileIds를 전달하면 기존 연결을 해제한 뒤 다시 연결한다`() {
        givenFoundNotDeleted(jobOf(status = JobStatus.DRAFT))
        givenActiveCompany()

        service.update(1L, JobUpdateRequest(fileIds = listOf(3L)), REQUESTER_ID)

        val ordered = inOrder(fileLinkPort)
        ordered.verify(fileLinkPort).unlinkAllOf(FileOwnerType.JOB, 1L)
        ordered.verify(fileLinkPort).validateAndLink(
            requesterId = REQUESTER_ID,
            fileIds = listOf(3L),
            purpose = FilePurpose.JOB_ATTACHMENT,
            ownerId = 1L,
        )
    }

    @Test
    fun `수정 시 fileIds로 빈 배열을 전달하면 전체 해제만 하고 다시 연결하지 않는다`() {
        givenFoundNotDeleted(jobOf(status = JobStatus.DRAFT))
        givenActiveCompany()

        service.update(1L, JobUpdateRequest(fileIds = emptyList()), REQUESTER_ID)

        verify(fileLinkPort).unlinkAllOf(FileOwnerType.JOB, 1L)
        verify(fileLinkPort, never()).validateAndLink(anyLong(), anyList(), anyPurpose(), anyLong())
    }

    @Test
    fun `수정 시 타인이 업로드한 파일을 재전송하면 FILE_NOT_OWNED로 거부한다`() {
        givenFoundNotDeleted(jobOf(status = JobStatus.DRAFT))
        givenActiveCompany()
        given(
            fileLinkPort.validateAndLink(
                requesterId = REQUESTER_ID,
                fileIds = listOf(3L),
                purpose = FilePurpose.JOB_ATTACHMENT,
                ownerId = 1L,
            ),
        ).willThrow(FileNotOwnedException(3L))

        assertThatThrownBy {
            service.update(1L, JobUpdateRequest(fileIds = listOf(3L)), REQUESTER_ID)
        }.isInstanceOf(FileNotOwnedException::class.java)
    }

    @Test
    fun `공개 상태 공고의 상세 응답에는 첨부파일 목록이 담긴다`() {
        val job = jobOf(status = JobStatus.PUBLISHED, createdByMemberId = 1L)
        givenFoundNotDeleted(job)
        givenActiveCompany()
        given(fileLinkPort.linkedFilesOf(FileOwnerType.JOB, 1L)).willReturn(listOf(fileSnapshotOf(5L)))

        val response = service.getPublicDetail(1L, REQUESTER_ID)

        assertThat(response.files).extracting("fileId").containsExactly(5L)
    }

    @Test
    fun `공개 상태 공고는 조기 반환으로 findRoles를 호출하지 않는다`() {
        val job = jobOf(status = JobStatus.PUBLISHED, createdByMemberId = 1L)
        givenFoundNotDeleted(job)
        givenActiveCompany()

        service.getPublicDetail(1L, REQUESTER_ID)

        verify(memberRoleQueryPort, never()).findRoles(anyLong())
    }

    @Test
    fun `DRAFT 공고는 등록자·담당 교사·개발자가 아니면 상세 응답의 첨부파일 목록이 비어 있다`() {
        val job = jobOf(status = JobStatus.DRAFT, createdByMemberId = 1L, managerMemberId = 2L)
        given(jobRepository.findById(1L)).willReturn(Optional.of(job))
        givenActiveCompany()
        given(memberRoleQueryPort.findRoles(REQUESTER_ID)).willReturn(setOf())

        val response = service.getForAdmin(1L, REQUESTER_ID)

        assertThat(response.files).isEmpty()
        verify(fileLinkPort, never()).linkedFilesOf(FileOwnerType.JOB, 1L)
    }

    @Test
    fun `DRAFT 공고는 등록자가 상세 응답에서 첨부파일 목록을 볼 수 있다`() {
        val job = jobOf(status = JobStatus.DRAFT, createdByMemberId = REQUESTER_ID)
        given(jobRepository.findById(1L)).willReturn(Optional.of(job))
        givenActiveCompany()
        given(fileLinkPort.linkedFilesOf(FileOwnerType.JOB, 1L)).willReturn(listOf(fileSnapshotOf(5L)))

        val response = service.getForAdmin(1L, REQUESTER_ID)

        assertThat(response.files).extracting("fileId").containsExactly(5L)
    }

    @Test
    fun `DRAFT 공고는 개발자가 상세 응답에서 첨부파일 목록을 볼 수 있다`() {
        val job = jobOf(status = JobStatus.DRAFT, createdByMemberId = 1L)
        given(jobRepository.findById(1L)).willReturn(Optional.of(job))
        givenActiveCompany()
        given(memberRoleQueryPort.findRoles(REQUESTER_ID)).willReturn(setOf(RoleType.DEVELOPER))
        given(fileLinkPort.linkedFilesOf(FileOwnerType.JOB, 1L)).willReturn(listOf(fileSnapshotOf(5L)))

        val response = service.getForAdmin(1L, REQUESTER_ID)

        assertThat(response.files).extracting("fileId").containsExactly(5L)
    }

    // --- 공개 상세와 조회수 ---

    @Test
    fun `공개 상세를 조회하면 조회수를 원자적으로 증가시키고 증가된 값을 응답한다`() {
        val job = jobOf(status = JobStatus.PUBLISHED, viewCount = 10).apply { sourceName = "MMA" }
        givenFoundNotDeleted(job)
        givenActiveCompany()

        val response = service.getPublicDetail(1L, REQUESTER_ID)

        assertThat(response.viewCount).isEqualTo(11)
        assertThat(response.sourceName).isEqualTo("MMA")
        verify(jobRepository).incrementViewCount(1L)
    }

    @Test
    fun `공개 상세 응답에는 요청자 기준 지원 가능 여부가 담긴다`() {
        val job = jobOf(status = JobStatus.PUBLISHED)
        givenFoundNotDeleted(job)
        givenActiveCompany()
        // service를 먼저 참조해 by lazy 초기화 블록의 기본 Stub(모든 jobId에 AVAILABLE)을 먼저
        // 등록시킨 뒤, 이 Test만의 값으로 재정의한다 -- Mockito는 나중에 등록된 Stub을 우선하므로
        // 순서를 반대로 하면(재정의를 먼저 하면) 기본 Stub이 이 값을 덮어써 버린다.
        service
        val eligibility =
            JobApplicationEligibilityAccessSnapshot(
                canApply = false,
                eligibilityReason = "ALREADY_APPLIED",
                eligibilityMessage = "이미 이 공고에 지원한 이력이 있습니다.",
                applicationId = 42L,
                applicationStatus = "SUBMITTED",
                availableActions = listOf("REQUEST_EDIT", "WITHDRAW"),
            )
        given(jobApplicationEligibilityAccessor.findAllByJobIds(setOf(1L), REQUESTER_ID))
            .willReturn(mapOf(1L to eligibility))

        val response = service.getPublicDetail(1L, REQUESTER_ID)

        assertThat(response.application).isEqualTo(eligibility)
    }

    @Test
    fun `공개 상세 응답에는 요청자 기준 북마크 여부가 담긴다`() {
        val job = jobOf(status = JobStatus.PUBLISHED)
        givenFoundNotDeleted(job)
        givenActiveCompany()
        // service를 먼저 참조해 기본 Stub(bookmarked=false)을 등록시킨 뒤 이 Test만의 값으로
        // 재정의한다(위 지원 가능 여부 Test와 같은 이유).
        service
        given(jobBookmarkAccessor.findAllByJobIds(setOf(1L), REQUESTER_ID)).willReturn(setOf(1L))

        val response = service.getPublicDetail(1L, REQUESTER_ID)

        assertThat(response.bookmarked).isTrue()
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

    private fun anyPurpose(): FilePurpose = any(FilePurpose::class.java) ?: FilePurpose.JOB_ATTACHMENT

    private fun fileSnapshotOf(fileId: Long) =
        FileSnapshot(fileId = fileId, originalName = "첨부파일.pdf", contentType = "application/pdf", size = 1024L)

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
        location: String? = null,
        employmentType: String? = null,
        fileIds: List<Long> = emptyList(),
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
        location = location,
        employmentType = employmentType,
        fileIds = fileIds,
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
        createdByMemberId: Long? = null,
        managerMemberId: Long? = null,
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
        this.createdByMemberId = createdByMemberId
        this.managerMemberId = managerMemberId
    }

    private companion object {
        private const val REQUESTER_ID = 7L
        private const val LOGO_URL = "https://storage.example/company-logo?signature=test"
    }
}
