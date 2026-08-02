package team.inreok.getiserver.domain.job.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyCollection
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import team.inreok.getiserver.domain.company.query.CompanyQuery
import team.inreok.getiserver.domain.company.query.CompanySummary
import team.inreok.getiserver.domain.job.dto.JobCreateRequest
import team.inreok.getiserver.domain.job.dto.JobSort
import team.inreok.getiserver.domain.job.dto.JobStatusUpdateRequest
import team.inreok.getiserver.domain.job.dto.JobUpdateRequest
import team.inreok.getiserver.domain.job.dto.PublicJobStatus
import team.inreok.getiserver.domain.job.entity.Job
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.entity.type.PostingType
import team.inreok.getiserver.domain.job.exception.JobCompanyNotFoundException
import team.inreok.getiserver.domain.job.exception.JobFormRequiredException
import team.inreok.getiserver.domain.job.exception.JobNotFoundException
import team.inreok.getiserver.domain.job.exception.JobNotVisibleException
import team.inreok.getiserver.domain.job.exception.JobStatusTransitionInvalidException
import team.inreok.getiserver.domain.job.exception.JobValidationFailedException
import team.inreok.getiserver.domain.job.repository.JobRepository
import team.inreok.getiserver.domain.job.service.impl.JobServiceImpl
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

    @Captor
    private lateinit var jobCaptor: ArgumentCaptor<Job>

    @Captor
    private lateinit var statusesCaptor: ArgumentCaptor<Collection<JobStatus>>

    @Captor
    private lateinit var queryCaptor: ArgumentCaptor<String>

    @Captor
    private lateinit var pageableCaptor: ArgumentCaptor<Pageable>

    private val service: JobService by lazy { JobServiceImpl(jobRepository, companyQuery) }

    private val companySummary = CompanySummary(companyId = 1L, name = "인력개발원")

    // --- 등록 ---

    @Test
    fun `DRAFT는 본문과 지원 URL이 없어도 임시저장된다`() {
        givenActiveCompany()
        givenSaveAssignsId()

        val response = service.create(draftRequest(), createdByMemberId = 7L)

        assertThat(response.status).isEqualTo(JobStatus.DRAFT)
        assertThat(response.content).isNull()
        assertThat(response.publishedAt).isNull()
        assertThat(response.company).isEqualTo(companySummary)
    }

    @Test
    fun `등록 시 작성자를 Access Token에서 받은 회원 ID로 기록한다`() {
        givenActiveCompany()
        givenSaveAssignsId()

        service.create(draftRequest(), createdByMemberId = 7L)

        verify(jobRepository).saveAndFlush(jobCaptor.capture() ?: newJob())
        assertThat(jobCaptor.value.createdByMemberId).isEqualTo(7L)
        // 담당자는 요청으로 받지 않으므로 항상 비어 있어야 한다(Issue #60 제외 범위).
        assertThat(jobCaptor.value.managerMemberId).isNull()
    }

    @Test
    fun `삭제되었거나 없는 기업으로 등록하면 COMPANY_NOT_FOUND로 거부한다`() {
        given(companyQuery.findActiveSummary(anyLong())).willReturn(null)

        assertThatThrownBy { service.create(draftRequest(), 7L) }
            .isInstanceOf(JobCompanyNotFoundException::class.java)

        verify(jobRepository, never()).saveAndFlush(anyJob())
    }

    @Test
    fun `PUBLISHED로 등록하면 게시 시각을 기록한다`() {
        givenActiveCompany()
        givenSaveAssignsId()

        val response = service.create(publishableRequest(), 7L)

        assertThat(response.status).isEqualTo(JobStatus.PUBLISHED)
        assertThat(response.publishedAt).isNotNull()
    }

    @Test
    fun `PUBLISHED로 등록할 때 본문이 없으면 거부한다`() {
        givenActiveCompany()

        assertThatThrownBy { service.create(publishableRequest(content = null), 7L) }
            .isInstanceOf(JobValidationFailedException::class.java)
    }

    @Test
    fun `PUBLISHED로 등록할 때 외부 지원 URL이 없으면 거부한다`() {
        givenActiveCompany()

        assertThatThrownBy { service.create(publishableRequest(externalUrl = null), 7L) }
            .isInstanceOf(JobValidationFailedException::class.java)
    }

    @Test
    fun `INTERNAL 공고는 게시할 수 없고 JOB_FORM_REQUIRED로 거부한다`() {
        givenActiveCompany()

        assertThatThrownBy {
            service.create(publishableRequest(applicationMethod = ApplicationMethod.INTERNAL), 7L)
        }.isInstanceOf(JobFormRequiredException::class.java)
    }

    @Test
    fun `INTERNAL 공고도 DRAFT로는 저장된다`() {
        givenActiveCompany()
        givenSaveAssignsId()

        val response = service.create(draftRequest(applicationMethod = ApplicationMethod.INTERNAL), 7L)

        assertThat(response.applicationMethod).isEqualTo(ApplicationMethod.INTERNAL)
    }

    @Test
    fun `CLOSED나 DELETED 상태로는 등록할 수 없다`() {
        givenActiveCompany()

        assertThatThrownBy { service.create(draftRequest(status = JobStatus.CLOSED), 7L) }
            .isInstanceOf(JobValidationFailedException::class.java)
        verify(jobRepository, never()).saveAndFlush(anyJob())
    }

    @Test
    fun `제목이 공백만 있으면 거부한다`() {
        givenActiveCompany()

        assertThatThrownBy { service.create(draftRequest(title = "   "), 7L) }
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
                7L,
            )
        }.isInstanceOf(JobValidationFailedException::class.java)
    }

    @Test
    fun `대상 학년이 1에서 3 밖이면 거부한다`() {
        givenActiveCompany()

        assertThatThrownBy { service.create(draftRequest(targetGrade = 4), 7L) }
            .isInstanceOf(JobValidationFailedException::class.java)
    }

    @Test
    fun `모집 인원이 0 이하면 거부한다`() {
        givenActiveCompany()

        assertThatThrownBy { service.create(draftRequest(capacity = 0), 7L) }
            .isInstanceOf(JobValidationFailedException::class.java)
    }

    @Test
    fun `외부 지원 URL이 http나 https가 아니면 거부한다`() {
        givenActiveCompany()

        assertThatThrownBy { service.create(draftRequest(externalUrl = "ftp://example.com/apply"), 7L) }
            .isInstanceOf(JobValidationFailedException::class.java)
    }

    // --- 수정 ---

    @Test
    fun `전달한 Field만 수정하고 나머지는 유지한다`() {
        val job = jobOf(status = JobStatus.DRAFT, title = "원래 제목", capacity = 5)
        givenFoundNotDeleted(job)
        givenActiveCompany()

        service.update(1L, JobUpdateRequest(title = "새 제목"))

        assertThat(job.title).isEqualTo("새 제목")
        assertThat(job.capacity).isEqualTo(5)
    }

    @Test
    fun `게시된 공고를 수정해 제목을 비우면 거부한다`() {
        val job = jobOf(status = JobStatus.PUBLISHED)
        givenFoundNotDeleted(job)

        assertThatThrownBy { service.update(1L, JobUpdateRequest(title = "   ")) }
            .isInstanceOf(JobValidationFailedException::class.java)
    }

    @Test
    fun `없거나 삭제된 공고를 수정하면 JOB_NOT_FOUND로 거부한다`() {
        given(jobRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(null)

        assertThatThrownBy { service.update(1L, JobUpdateRequest(title = "새 제목")) }
            .isInstanceOf(JobNotFoundException::class.java)
    }

    // --- 상태 전이 ---

    @Test
    fun `DRAFT를 게시하면 게시 시각을 기록한다`() {
        val job = jobOf(status = JobStatus.DRAFT)
        givenFoundNotDeleted(job)
        givenActiveCompany()

        val response = service.changeStatus(1L, JobStatusUpdateRequest(JobStatus.PUBLISHED))

        assertThat(response.status).isEqualTo(JobStatus.PUBLISHED)
        assertThat(job.publishedAt).isNotNull()
    }

    @Test
    fun `게시 필수값을 갖추지 못한 DRAFT는 게시할 수 없다`() {
        val job = jobOf(status = JobStatus.DRAFT, content = null)
        givenFoundNotDeleted(job)

        assertThatThrownBy { service.changeStatus(1L, JobStatusUpdateRequest(JobStatus.PUBLISHED)) }
            .isInstanceOf(JobValidationFailedException::class.java)
        assertThat(job.status).isEqualTo(JobStatus.DRAFT)
    }

    @Test
    fun `INTERNAL 공고는 상태 변경으로도 게시할 수 없다`() {
        val job = jobOf(status = JobStatus.DRAFT, applicationMethod = ApplicationMethod.INTERNAL)
        givenFoundNotDeleted(job)

        assertThatThrownBy { service.changeStatus(1L, JobStatusUpdateRequest(JobStatus.PUBLISHED)) }
            .isInstanceOf(JobFormRequiredException::class.java)
    }

    @Test
    fun `게시된 공고를 마감하면 마감 시각을 기록한다`() {
        val job = jobOf(status = JobStatus.PUBLISHED)
        givenFoundNotDeleted(job)
        givenActiveCompany()

        service.changeStatus(1L, JobStatusUpdateRequest(JobStatus.CLOSED))

        assertThat(job.status).isEqualTo(JobStatus.CLOSED)
        assertThat(job.closedAt).isNotNull()
    }

    @Test
    fun `삭제는 Soft Delete로 상태와 삭제 시각을 함께 기록한다`() {
        val job = jobOf(status = JobStatus.PUBLISHED)
        givenFoundNotDeleted(job)
        givenActiveCompany()

        service.changeStatus(1L, JobStatusUpdateRequest(JobStatus.DELETED))

        assertThat(job.status).isEqualTo(JobStatus.DELETED)
        assertThat(job.deletedAt).isNotNull()
        // 실제 행을 지우지 않아야 북마크와 지원 이력이 보존된다.
        verify(jobRepository, never()).delete(anyJob())
    }

    @Test
    fun `마감된 공고를 다시 게시할 수 없다`() {
        givenFoundNotDeleted(jobOf(status = JobStatus.CLOSED))

        assertThatThrownBy { service.changeStatus(1L, JobStatusUpdateRequest(JobStatus.PUBLISHED)) }
            .isInstanceOf(JobStatusTransitionInvalidException::class.java)
    }

    @Test
    fun `같은 상태로의 변경도 거부한다`() {
        givenFoundNotDeleted(jobOf(status = JobStatus.PUBLISHED))

        assertThatThrownBy { service.changeStatus(1L, JobStatusUpdateRequest(JobStatus.PUBLISHED)) }
            .isInstanceOf(JobStatusTransitionInvalidException::class.java)
    }

    @Test
    fun `이미 삭제된 공고는 상태를 바꿀 수 없다`() {
        given(jobRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(null)

        assertThatThrownBy { service.changeStatus(1L, JobStatusUpdateRequest(JobStatus.PUBLISHED)) }
            .isInstanceOf(JobNotFoundException::class.java)
    }

    // --- 공개 상세와 조회수 ---

    @Test
    fun `공개 상세를 조회하면 조회수를 원자적으로 증가시키고 증가된 값을 응답한다`() {
        val job = jobOf(status = JobStatus.PUBLISHED, viewCount = 10)
        givenFoundNotDeleted(job)
        givenActiveCompany()

        val response = service.getPublicDetail(1L)

        assertThat(response.viewCount).isEqualTo(11)
        verify(jobRepository).incrementViewCount(1L)
    }

    @Test
    fun `임시저장 공고를 공개 상세로 조회하면 JOB_NOT_VISIBLE로 거부한다`() {
        givenFoundNotDeleted(jobOf(status = JobStatus.DRAFT))

        assertThatThrownBy { service.getPublicDetail(1L) }
            .isInstanceOf(JobNotVisibleException::class.java)
        verify(jobRepository, never()).incrementViewCount(anyLong())
    }

    @Test
    fun `마감된 공고는 공개 상세로 조회할 수 있다`() {
        givenFoundNotDeleted(jobOf(status = JobStatus.CLOSED))
        givenActiveCompany()

        val response = service.getPublicDetail(1L)

        assertThat(response.status).isEqualTo(JobStatus.CLOSED)
    }

    @Test
    fun `삭제된 공고는 공개 상세에서 JOB_NOT_FOUND로 처리한다`() {
        given(jobRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(null)

        assertThatThrownBy { service.getPublicDetail(1L) }
            .isInstanceOf(JobNotFoundException::class.java)
    }

    @Test
    fun `관리자 상세는 모든 상태를 조회하고 조회수를 올리지 않는다`() {
        val job = jobOf(status = JobStatus.DRAFT, viewCount = 10)
        given(jobRepository.findById(1L)).willReturn(Optional.of(job))
        givenActiveCompany()

        val response = service.getForAdmin(1L)

        assertThat(response.status).isEqualTo(JobStatus.DRAFT)
        assertThat(response.viewCount).isEqualTo(10)
        verify(jobRepository, never()).incrementViewCount(anyLong())
    }

    // --- 목록 ---

    @Test
    fun `필터를 지정하지 않으면 게시와 마감 상태만 조회한다`() {
        givenEmptySearch()

        service.searchPublic(null, null, null, false, JobSort.LATEST, PageRequest.of(0, 20))

        verifySearch()
        assertThat(statusesCaptor.value).containsExactlyInAnyOrder(JobStatus.PUBLISHED, JobStatus.CLOSED)
    }

    @Test
    fun `상태 필터를 지정하면 해당 상태만 조회한다`() {
        givenEmptySearch()

        service.searchPublic(null, null, PublicJobStatus.CLOSED, false, JobSort.LATEST, PageRequest.of(0, 20))

        verifySearch()
        assertThat(statusesCaptor.value).containsExactly(JobStatus.CLOSED)
    }

    @Test
    fun `검색어가 공백만 있으면 제목 조건 없이 조회한다`() {
        givenEmptySearch()

        service.searchPublic("   ", null, null, false, JobSort.LATEST, PageRequest.of(0, 20))

        verifySearch()
        assertThat(queryCaptor.value).isNull()
    }

    @Test
    fun `검색어의 LIKE Wildcard를 Escape하고 앞뒤 %를 붙여 Repository에 전달한다`() {
        givenEmptySearch()

        service.searchPublic("100%_할인", null, null, false, JobSort.LATEST, PageRequest.of(0, 20))

        verifySearch()
        assertThat(queryCaptor.value).isEqualTo("%100\\%\\_할인%")
    }

    @Test
    fun `검색어는 소문자 Pattern으로 변환해 대소문자를 구분하지 않는다`() {
        givenEmptySearch()

        service.searchPublic("BackEnd", null, null, false, JobSort.LATEST, PageRequest.of(0, 20))

        verifySearch()
        assertThat(queryCaptor.value).isEqualTo("%backend%")
    }

    @Test
    fun `정렬은 JobSort로만 결정되고 항상 공고 ID 보조 정렬이 붙는다`() {
        givenEmptySearch()

        // Pageable이 엉뚱한 정렬을 들고 와도 무시되어야 한다.
        val pageable = PageRequest.of(0, 20, Sort.by("bodyMarkdown"))
        service.searchPublic(null, null, null, false, JobSort.VIEWS, pageable)

        verifySearch()
        assertThat(pageableCaptor.value.sort.map { "${it.property}:${it.direction}" })
            .containsExactly("viewCount:DESC", "id:DESC")
    }

    @Test
    fun `최신순 정렬은 게시 시각 내림차순과 공고 ID 보조 정렬을 사용한다`() {
        givenEmptySearch()

        service.searchPublic(null, null, null, false, JobSort.LATEST, PageRequest.of(0, 20))

        verifySearch()
        assertThat(pageableCaptor.value.sort.map { "${it.property}:${it.direction}" })
            .containsExactly("publishedAt:DESC", "id:DESC")
    }

    @Test
    fun `목록은 기업 요약을 한 번만 조회해 N+1을 피한다`() {
        val jobs = listOf(jobOf(id = 1L), jobOf(id = 2L), jobOf(id = 3L))
        given(
            jobRepository.searchPublic(anyStatuses(), any(), any(), anyBoolean(), anyDateTime(), anyPageable()),
        ).willReturn(PageImpl(jobs, PageRequest.of(0, 20), 3))
        given(companyQuery.findActiveSummaries(anyStatusIds())).willReturn(mapOf(1L to companySummary))

        val response = service.searchPublic(null, null, null, false, JobSort.LATEST, PageRequest.of(0, 20))

        assertThat(response.data).hasSize(3)
        assertThat(response.meta.totalElements).isEqualTo(3)
        verify(companyQuery, times(1)).findActiveSummaries(anyStatusIds())
        verify(companyQuery, never()).findActiveSummary(anyLong())
    }

    // --- Fixture ---
    //
    // Kotlin non-null 파라미터에 bare any()를 쓰면 null이 반환되어 NPE가 나므로 Elvis로 기본값을
    // 준다(CompanyServiceTest.anyCompany와 같은 이유).

    private fun anyJob(): Job = any(Job::class.java) ?: newJob()

    private fun anyPageable(): Pageable = any(Pageable::class.java) ?: PageRequest.of(0, 20)

    private fun anyDateTime(): LocalDateTime = any(LocalDateTime::class.java) ?: LocalDateTime.now()

    private fun anyStatuses(): Collection<JobStatus> = anyCollection() ?: emptyList()

    private fun anyStatusIds(): Collection<Long> = anyCollection() ?: emptyList()

    private fun verifySearch() {
        verify(jobRepository).searchPublic(
            statusesCaptor.capture() ?: emptyList(),
            queryCaptor.capture(),
            any(),
            anyBoolean(),
            anyDateTime(),
            pageableCaptor.capture() ?: PageRequest.of(0, 20),
        )
    }

    private fun givenActiveCompany() {
        given(companyQuery.findActiveSummary(1L)).willReturn(companySummary)
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

    private fun givenEmptySearch() {
        given(
            jobRepository.searchPublic(anyStatuses(), any(), any(), anyBoolean(), anyDateTime(), anyPageable()),
        ).willReturn(PageImpl(emptyList(), PageRequest.of(0, 20), 0))
        given(companyQuery.findActiveSummaries(anyStatusIds())).willReturn(emptyMap())
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
}
