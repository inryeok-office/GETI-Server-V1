package team.inreok.getiserver.domain.application.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import team.inreok.getiserver.domain.application.dto.JobApplicationAdminAction
import team.inreok.getiserver.domain.application.dto.JobApplicationAdminActionRequest
import team.inreok.getiserver.domain.application.entity.JobApplication
import team.inreok.getiserver.domain.application.entity.JobApplicationStatusHistory
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus
import team.inreok.getiserver.domain.application.event.JobApplicationReviewedEvent
import team.inreok.getiserver.domain.application.exception.ApplicationActionNotAvailableException
import team.inreok.getiserver.domain.application.exception.ApplicationNotFoundException
import team.inreok.getiserver.domain.application.exception.ApplicationReviewForbiddenException
import team.inreok.getiserver.domain.application.repository.JobApplicationRepository
import team.inreok.getiserver.domain.application.repository.JobApplicationStatusHistoryRepository
import team.inreok.getiserver.domain.application.service.JobApplicationAdminService
import team.inreok.getiserver.domain.company.query.CompanyQuery
import team.inreok.getiserver.domain.company.query.CompanySummary
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.file.link.FileLinkPort
import team.inreok.getiserver.domain.file.link.FileSnapshot
import team.inreok.getiserver.domain.job.query.JobApplicationAdminFilterQueryPort
import team.inreok.getiserver.domain.job.query.JobApplicationJobSnapshot
import team.inreok.getiserver.domain.job.query.JobApplicationSnapshotQueryPort
import team.inreok.getiserver.domain.member.entity.type.DepartmentType
import team.inreok.getiserver.domain.member.query.InquiryMemberSnapshot
import team.inreok.getiserver.domain.member.query.InquiryMemberSnapshotQueryPort
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
    private lateinit var jobApplicationAdminFilterQueryPort: JobApplicationAdminFilterQueryPort

    @Mock
    private lateinit var jobApplicationStatusHistoryRepository: JobApplicationStatusHistoryRepository

    @Mock
    private lateinit var fileLinkPort: FileLinkPort

    @Mock
    private lateinit var companyQuery: CompanyQuery

    @Mock
    private lateinit var inquiryMemberSnapshotQueryPort: InquiryMemberSnapshotQueryPort

    @Mock
    private lateinit var eventPublisher: ApplicationEventPublisher

    private val service: JobApplicationAdminService by lazy {
        JobApplicationAdminServiceImpl(
            jobApplicationRepository,
            jobApplicationSnapshotQueryPort,
            jobApplicationAdminFilterQueryPort,
            jobApplicationStatusHistoryRepository,
            fileLinkPort,
            companyQuery,
            inquiryMemberSnapshotQueryPort,
            eventPublisher,
            JsonMapper(),
        )
    }

    private val fixedTime = LocalDateTime.of(2026, 3, 1, 10, 0, 0)

    private fun applicationOf(
        id: Long = 1L,
        status: JobApplicationStatus = JobApplicationStatus.SUBMITTED,
        applicantMemberId: Long = 1L,
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

    // 기본(무필터) search() 호출을 Stub한다 -- Filter 관련 Test는 각자 필요한 Parameter만 다르게
    // Stub한다(아래 Filter 절 참고). 이 목록 조회는 applicationIds Filter를 받지 않으므로(Issue #203
    // 범위 밖, JobApplicationAdminServiceImpl.list 참고) 항상 hasApplicationIds=false로 호출된다.
    private fun givenDefaultSearch(page: Page<JobApplication>) {
        given(
            jobApplicationRepository.search(
                jobId = null,
                status = null,
                hasApplicantName = false,
                applicantName = "",
                cohort = null,
                department = null,
                hasJobFilter = false,
                jobIds = emptySet(),
                hasApplicationIds = false,
                applicationIds = emptySet(),
                pageable = PageRequest.of(0, 20),
            ),
        ).willReturn(page)
    }

    private fun listOf20(
        jobId: Long? = null,
        status: JobApplicationStatus? = null,
        applicantName: String? = null,
        cohort: Int? = null,
        department: DepartmentType? = null,
        companyId: Long? = null,
        managerMemberId: Long? = null,
        mineOnly: Boolean = false,
        requesterMemberId: Long = 1L,
        pageable: Pageable = PageRequest.of(0, 20),
    ) = service.list(
        jobId,
        status,
        applicantName,
        cohort,
        department,
        companyId,
        managerMemberId,
        mineOnly,
        requesterMemberId,
        pageable,
    )

    @Test
    fun `목록을 조회하면 항목을 반환한다`() {
        val page = PageImpl(listOf(applicationOf()), PageRequest.of(0, 20), 1)
        givenDefaultSearch(page)

        val result = listOf20()

        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].applicationId).isEqualTo(1L)
        assertThat(result.totalElements).isEqualTo(1)
    }

    // Issue #172 -- 목록 응답에 기수·학과(Snapshot)·공고명·기업명·담당자가 채워지는지 확인한다.
    @Test
    fun `목록 조회 결과에는 기수학과·공고명·기업명·담당자가 채워진다`() {
        val application =
            applicationOf().apply {
                applicantCohort = 5
                applicantDepartment = "SW"
            }
        val page = PageImpl(listOf(application), PageRequest.of(0, 20), 1)
        givenDefaultSearch(page)
        given(jobApplicationSnapshotQueryPort.findAllByIds(setOf(1L)))
            .willReturn(mapOf(1L to jobOf(createdByMemberId = 100L, managerMemberId = 200L)))
        given(companyQuery.findActiveSummaries(setOf(1L)))
            .willReturn(mapOf(1L to CompanySummary(companyId = 1L, name = "인력개발원")))
        given(inquiryMemberSnapshotQueryPort.findAllByIds(setOf(200L)))
            .willReturn(mapOf(200L to InquiryMemberSnapshot(200L, "김담당", null, null, null, true)))

        val item = listOf20().content[0]

        assertThat(item.applicantCohort).isEqualTo(5)
        assertThat(item.applicantDepartment).isEqualTo("SW")
        assertThat(item.jobTitle).isEqualTo("인턴 채용")
        assertThat(item.companyName).isEqualTo("인력개발원")
        assertThat(item.managerMemberId).isEqualTo(200L)
        assertThat(item.managerName).isEqualTo("김담당")
    }

    // 담당 교사가 지정되지 않은 공고는 등록자로 대체하지 않고 빈 값으로 둔다(사용자 확인 완료,
    // Issue #172 DECISION_REQUIRED).
    @Test
    fun `공고에 담당 교사가 없으면 담당자 정보는 빈 값이다`() {
        val page = PageImpl(listOf(applicationOf()), PageRequest.of(0, 20), 1)
        givenDefaultSearch(page)
        given(jobApplicationSnapshotQueryPort.findAllByIds(setOf(1L)))
            .willReturn(mapOf(1L to jobOf(createdByMemberId = 100L, managerMemberId = null)))
        given(companyQuery.findActiveSummaries(setOf(1L))).willReturn(emptyMap())

        val item = listOf20().content[0]

        assertThat(item.managerMemberId).isNull()
        assertThat(item.managerName).isNull()
    }

    // 목록 항목마다 공고·기업·담당자를 개별 조회하면 N+1이 되므로(PR #172 목표), 여러 항목이 같은
    // 공고를 가리켜도 배치 조회 Method가 한 번만 호출되는지 확인한다.
    @Test
    fun `목록 항목이 여러 건이어도 공고·기업·담당자 배치 조회는 한 번만 호출된다`() {
        val applications = listOf(applicationOf(id = 1L), applicationOf(id = 2L))
        val page = PageImpl(applications, PageRequest.of(0, 20), 2)
        givenDefaultSearch(page)
        given(jobApplicationSnapshotQueryPort.findAllByIds(setOf(1L)))
            .willReturn(mapOf(1L to jobOf(createdByMemberId = 100L, managerMemberId = 200L)))
        given(companyQuery.findActiveSummaries(setOf(1L))).willReturn(emptyMap())
        given(inquiryMemberSnapshotQueryPort.findAllByIds(setOf(200L))).willReturn(emptyMap())

        listOf20()

        verify(jobApplicationSnapshotQueryPort).findAllByIds(setOf(1L))
        verify(companyQuery).findActiveSummaries(setOf(1L))
        verify(inquiryMemberSnapshotQueryPort).findAllByIds(setOf(200L))
    }

    // ---------- list Filter(Issue #181) ----------

    // applicantName은 검색어 앞뒤 공백을 제거하고 LIKE Wildcard를 이스케이프해 Repository에
    // 전달한다(escapeLikePattern, InquiryServiceImpl.listAdmin과 동일한 관례).
    @Test
    fun `applicantName Filter는 공백을 제거하고 LIKE Wildcard를 이스케이프해 전달한다`() {
        val page = PageImpl(emptyList<JobApplication>(), PageRequest.of(0, 20), 0)
        given(
            jobApplicationRepository.search(
                jobId = null,
                status = null,
                hasApplicantName = true,
                applicantName = "50\\%",
                cohort = null,
                department = null,
                hasJobFilter = false,
                jobIds = emptySet(),
                hasApplicationIds = false,
                applicationIds = emptySet(),
                pageable = PageRequest.of(0, 20),
            ),
        ).willReturn(page)

        val result = listOf20(applicantName = "  50%  ")

        assertThat(result.totalElements).isEqualTo(0)
    }

    // 검색어가 공백뿐이면 검색어 없음과 동일하게 취급한다(InquiryServiceImpl.listAdmin과 동일한 관례).
    @Test
    fun `applicantName이 공백뿐이면 Filter를 적용하지 않는다`() {
        val page = PageImpl(listOf(applicationOf()), PageRequest.of(0, 20), 1)
        givenDefaultSearch(page)

        val result = listOf20(applicantName = "   ")

        assertThat(result.totalElements).isEqualTo(1)
    }

    @Test
    fun `cohort와 department Filter를 그대로 전달한다`() {
        val page = PageImpl(emptyList<JobApplication>(), PageRequest.of(0, 20), 0)
        given(
            jobApplicationRepository.search(
                jobId = null,
                status = null,
                hasApplicantName = false,
                applicantName = "",
                cohort = 5,
                department = "AI",
                hasJobFilter = false,
                jobIds = emptySet(),
                hasApplicationIds = false,
                applicationIds = emptySet(),
                pageable = PageRequest.of(0, 20),
            ),
        ).willReturn(page)

        val result = listOf20(cohort = 5, department = DepartmentType.AI)

        assertThat(result.totalElements).isEqualTo(0)
    }

    // companyId만 지정되면 job 도메인에 배치 조회를 요청하고, 그 결과 jobId 집합을 jobIds Filter로
    // 전달한다.
    @Test
    fun `companyId Filter를 지정하면 job 도메인 조회 결과를 jobIds Filter로 전달한다`() {
        given(jobApplicationAdminFilterQueryPort.findIdsByFilters(1L, null, null)).willReturn(setOf(10L, 11L))
        val page = PageImpl(emptyList<JobApplication>(), PageRequest.of(0, 20), 0)
        given(
            jobApplicationRepository.search(
                jobId = null,
                status = null,
                hasApplicantName = false,
                applicantName = "",
                cohort = null,
                department = null,
                hasJobFilter = true,
                jobIds = setOf(10L, 11L),
                hasApplicationIds = false,
                applicationIds = emptySet(),
                pageable = PageRequest.of(0, 20),
            ),
        ).willReturn(page)

        val result = listOf20(companyId = 1L)

        assertThat(result.totalElements).isEqualTo(0)
    }

    // mineOnly=true면 requesterMemberId를 job 도메인 조회의 mineOnlyMemberId로 전달한다(담당
    // (managerMemberId) 또는 등록(createdByMemberId) 공고, 사용자 확인 완료).
    @Test
    fun `mineOnly가 true면 requesterMemberId 기준으로 job 도메인에 조회를 요청한다`() {
        given(jobApplicationAdminFilterQueryPort.findIdsByFilters(null, null, 100L)).willReturn(setOf(1L))
        val page = PageImpl(listOf(applicationOf()), PageRequest.of(0, 20), 1)
        given(
            jobApplicationRepository.search(
                jobId = null,
                status = null,
                hasApplicantName = false,
                applicantName = "",
                cohort = null,
                department = null,
                hasJobFilter = true,
                jobIds = setOf(1L),
                hasApplicationIds = false,
                applicationIds = emptySet(),
                pageable = PageRequest.of(0, 20),
            ),
        ).willReturn(page)

        val result = listOf20(mineOnly = true, requesterMemberId = 100L)

        assertThat(result.totalElements).isEqualTo(1)
        verify(jobApplicationAdminFilterQueryPort).findIdsByFilters(null, null, 100L)
    }

    // mineOnly가 false이고 companyId/managerMemberId도 없으면 job 도메인 배치 조회 자체를 호출하지
    // 않는다(불필요한 Query 방지).
    @Test
    fun `아무 Job Filter도 지정하지 않으면 job 도메인 배치 조회를 호출하지 않는다`() {
        val page = PageImpl(listOf(applicationOf()), PageRequest.of(0, 20), 1)
        givenDefaultSearch(page)

        listOf20()

        verify(jobApplicationAdminFilterQueryPort, never()).findIdsByFilters(any(), any(), any())
    }

    // companyId가 어떤 Job과도 일치하지 않으면 findIdsByFilters가 빈 집합을 반환한다. 이때
    // `a.jobId IN ()`으로 DB에 다녀올 필요가 없어 Repository.search 자체를 호출하지 않는다(PR #211
    // 코드리뷰 반영).
    @Test
    fun `Job Filter 결과가 빈 집합이면 Repository 조회 없이 빈 목록을 반환한다`() {
        given(jobApplicationAdminFilterQueryPort.findIdsByFilters(999L, null, null)).willReturn(emptySet())

        val result = listOf20(companyId = 999L)

        assertThat(result.content).isEmpty()
        assertThat(result.totalElements).isEqualTo(0)
        assertThat(result.page).isEqualTo(0)
        assertThat(result.size).isEqualTo(20)
        verifyNoInteractions(jobApplicationRepository)
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
    fun `상세 조회 결과에는 공고명·기업명·담당자가 채워진다`() {
        given(jobApplicationRepository.findById(1L)).willReturn(Optional.of(applicationOf()))
        given(jobApplicationSnapshotQueryPort.findById(1L))
            .willReturn(jobOf(createdByMemberId = 100L, managerMemberId = 200L))
        given(companyQuery.findActiveSummary(1L)).willReturn(CompanySummary(companyId = 1L, name = "인력개발원"))
        given(inquiryMemberSnapshotQueryPort.findAllByIds(setOf(200L)))
            .willReturn(mapOf(200L to InquiryMemberSnapshot(200L, "김담당", null, null, null, true)))

        val result = service.getDetail(1L)

        assertThat(result.jobTitle).isEqualTo("인턴 채용")
        assertThat(result.companyName).isEqualTo("인력개발원")
        assertThat(result.managerMemberId).isEqualTo(200L)
        assertThat(result.managerName).isEqualTo("김담당")
    }

    @Test
    fun `상세 조회 시 DRAFT 상태면 ApplicationNotFoundException을 던진다`() {
        given(
            jobApplicationRepository.findById(1L),
        ).willReturn(Optional.of(applicationOf(status = JobApplicationStatus.DRAFT)))

        assertThatThrownBy { service.getDetail(1L) }.isInstanceOf(ApplicationNotFoundException::class.java)
    }

    // PR #142 Review 반영 -- FileLinkPort Mock만 추가됐을 뿐 응답의 files 매핑을 단정하지 않았다.
    @Test
    fun `상세 조회 응답에는 File 도메인에 연결된 첨부파일 목록이 담긴다`() {
        given(jobApplicationRepository.findById(1L)).willReturn(Optional.of(applicationOf()))
        given(fileLinkPort.linkedFilesOf(FileOwnerType.JOB_APPLICATION, 1L))
            .willReturn(
                listOf(
                    FileSnapshot(fileId = 9L, originalName = "resume.pdf", contentType = "application/pdf", size = 200),
                ),
            )

        val result = service.getDetail(1L)

        assertThat(result.files).hasSize(1)
        assertThat(result.files[0].fileId).isEqualTo(9L)
        assertThat(result.files[0].downloadUrl).isEqualTo("/api/v1/files/9/download")
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

    // Issue #172 -- Action 수행 결과 응답에도 상세 조회와 동일하게 공고명·기업명·담당자가 채워진다.
    @Test
    fun `Action 수행 결과에는 공고명·기업명·담당자가 채워진다`() {
        given(
            jobApplicationRepository.findByIdForUpdate(1L),
        ).willReturn(applicationOf(status = JobApplicationStatus.SUBMITTED))
        given(jobApplicationSnapshotQueryPort.findById(1L))
            .willReturn(jobOf(createdByMemberId = 100L, managerMemberId = 200L))
        given(companyQuery.findActiveSummary(1L)).willReturn(CompanySummary(companyId = 1L, name = "인력개발원"))
        given(inquiryMemberSnapshotQueryPort.findAllByIds(setOf(200L)))
            .willReturn(mapOf(200L to InquiryMemberSnapshot(200L, "김담당", null, null, null, true)))

        val result =
            service.executeAction(
                1L,
                100L,
                isDeveloper = false,
                JobApplicationAdminActionRequest(JobApplicationAdminAction.APPROVE),
            )

        assertThat(result.jobTitle).isEqualTo("인턴 채용")
        assertThat(result.companyName).isEqualTo("인력개발원")
        assertThat(result.managerMemberId).isEqualTo(200L)
        assertThat(result.managerName).isEqualTo("김담당")
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

    // PR #142 Review(SUBMIT 파일 연결 회귀 Test 부재)와 동일한 이유로, 이 Action이 실제로
    // JobApplicationReviewedEvent를 발행하는지 단정한다(Issue #135).
    @Test
    fun `교사 Action이 성공하면 지원자에게 JobApplicationReviewedEvent를 발행한다`() {
        given(
            jobApplicationRepository.findByIdForUpdate(1L),
        ).willReturn(applicationOf(status = JobApplicationStatus.SUBMITTED, applicantMemberId = 7L))
        given(jobApplicationSnapshotQueryPort.findById(1L)).willReturn(jobOf(createdByMemberId = 100L))

        service.executeAction(
            1L,
            100L,
            isDeveloper = false,
            JobApplicationAdminActionRequest(JobApplicationAdminAction.APPROVE),
        )

        verify(eventPublisher).publishEvent(
            JobApplicationReviewedEvent(applicationId = 1L, studentMemberId = 7L, action = "APPROVE", reason = null),
        )
    }

    // 코드리뷰 반영(PR #143) -- APPROVE 하나만으로는 reason이 실리는 경로를 검증하지 못해
    // 사유 전달이 핵심인 REQUEST_REVISION도 함께 단정한다.
    @Test
    fun `REQUEST_REVISION이 성공하면 사유를 담은 JobApplicationReviewedEvent를 발행한다`() {
        given(
            jobApplicationRepository.findByIdForUpdate(1L),
        ).willReturn(applicationOf(status = JobApplicationStatus.SUBMITTED, applicantMemberId = 7L))
        given(jobApplicationSnapshotQueryPort.findById(1L)).willReturn(jobOf(createdByMemberId = 100L))

        service.executeAction(
            1L,
            100L,
            isDeveloper = false,
            JobApplicationAdminActionRequest(
                JobApplicationAdminAction.REQUEST_REVISION,
                reason = "포트폴리오 링크를 추가해주세요.",
            ),
        )

        verify(eventPublisher).publishEvent(
            JobApplicationReviewedEvent(
                applicationId = 1L,
                studentMemberId = 7L,
                action = "REQUEST_REVISION",
                reason = "포트폴리오 링크를 추가해주세요.",
            ),
        )
    }

    @Test
    fun `Action이 거부되면 Event를 발행하지 않는다`() {
        given(
            jobApplicationRepository.findByIdForUpdate(1L),
        ).willReturn(applicationOf(status = JobApplicationStatus.DRAFT, applicantMemberId = 7L))
        given(jobApplicationSnapshotQueryPort.findById(1L)).willReturn(jobOf(createdByMemberId = 100L))

        assertThatThrownBy {
            service.executeAction(
                1L,
                100L,
                isDeveloper = false,
                JobApplicationAdminActionRequest(JobApplicationAdminAction.APPROVE),
            )
        }.isInstanceOf(ApplicationActionNotAvailableException::class.java)

        verify(eventPublisher, never()).publishEvent(any(JobApplicationReviewedEvent::class.java) ?: dummyEvent())
    }

    // Kotlin non-null 파라미터에 bare any()를 쓰면 null 반환으로 NPE가 나므로 non-null Fallback을 둔다
    // (OAuthMemberPortImplTest.anyMember와 동일한 판단).
    private fun dummyEvent() =
        JobApplicationReviewedEvent(applicationId = 0L, studentMemberId = 0L, action = "", reason = null)

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
