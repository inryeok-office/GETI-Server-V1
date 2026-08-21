package team.inreok.getiserver.domain.portfolio.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.link.FileLinkPort
import team.inreok.getiserver.domain.file.link.FileSnapshot
import team.inreok.getiserver.domain.portfolio.dto.PortfolioSubmissionUpsertRequest
import team.inreok.getiserver.domain.portfolio.entity.PortfolioRequest
import team.inreok.getiserver.domain.portfolio.entity.PortfolioSubmission
import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioRequestStatus
import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioSubmissionStatus
import team.inreok.getiserver.domain.portfolio.exception.NotRequestTargetException
import team.inreok.getiserver.domain.portfolio.exception.PortfolioRequestNotFoundException
import team.inreok.getiserver.domain.portfolio.exception.SubmissionClosedException
import team.inreok.getiserver.domain.portfolio.repository.PortfolioRequestRepository
import team.inreok.getiserver.domain.portfolio.repository.PortfolioRequestTargetRepository
import team.inreok.getiserver.domain.portfolio.repository.PortfolioSubmissionRepository
import team.inreok.getiserver.domain.portfolio.service.impl.PortfolioSubmissionServiceImpl
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class PortfolioSubmissionServiceTest {
    @Mock
    private lateinit var requestRepository: PortfolioRequestRepository

    @Mock
    private lateinit var targetRepository: PortfolioRequestTargetRepository

    @Mock
    private lateinit var submissionRepository: PortfolioSubmissionRepository

    @Mock
    private lateinit var fileLinkPort: FileLinkPort

    private val service by lazy {
        PortfolioSubmissionServiceImpl(requestRepository, targetRepository, submissionRepository, fileLinkPort)
    }

    @Test
    fun `대상 학생이 제출하면 SUBMITTED로 저장하고 제출 시각과 파일을 반영한다`() {
        givenPublishedRequest()
        givenIsTarget()
        givenNoExistingSubmission()
        givenSaveAssignsId(100L)
        given(fileLinkPort.linkedFilesOf(FileOwnerType.PORTFOLIO_SUBMISSION, 100L)).willReturn(emptyList())
        given(fileLinkPort.validateAndLink(REQUESTER_ID, listOf(1L, 2L), FilePurpose.PORTFOLIO, 100L))
            .willReturn(listOf(snapshot(1L), snapshot(2L)))

        val response =
            service.submit(
                REQUEST_ID,
                REQUESTER_ID,
                upsert(PortfolioSubmissionStatus.SUBMITTED, fileIds = listOf(1L, 2L)),
            )

        assertThat(response.submissionId).isEqualTo(100L)
        assertThat(response.status).isEqualTo(PortfolioSubmissionStatus.SUBMITTED)
        assertThat(response.submittedAt).isNotNull()
        assertThat(response.files.map { it.fileId }).containsExactly(1L, 2L)
        assertThat(response.files[0].downloadUrl).isEqualTo("/api/v1/files/1/download")
    }

    @Test
    fun `임시저장은 제출 시각을 남기지 않고 파일이 없으면 연결하지 않는다`() {
        givenPublishedRequest()
        givenIsTarget()
        givenNoExistingSubmission()
        givenSaveAssignsId(101L)
        given(fileLinkPort.linkedFilesOf(FileOwnerType.PORTFOLIO_SUBMISSION, 101L)).willReturn(emptyList())

        val response = service.submit(REQUEST_ID, REQUESTER_ID, upsert(PortfolioSubmissionStatus.DRAFT))

        assertThat(response.status).isEqualTo(PortfolioSubmissionStatus.DRAFT)
        assertThat(response.submittedAt).isNull()
        assertThat(response.files).isEmpty()
        verify(fileLinkPort, never()).validateAndLink(REQUESTER_ID, emptyList(), FilePurpose.PORTFOLIO, 101L)
    }

    @Test
    fun `대상이 아닌 학생이 제출하면 NOT_REQUEST_TARGET을 던진다`() {
        givenPublishedRequest()
        given(targetRepository.existsByRequestIdAndStudentMemberId(REQUEST_ID, REQUESTER_ID)).willReturn(false)

        assertThatThrownBy { service.submit(REQUEST_ID, REQUESTER_ID, upsert(PortfolioSubmissionStatus.SUBMITTED)) }
            .isInstanceOf(NotRequestTargetException::class.java)
        verify(submissionRepository, never()).saveAndFlush(any())
    }

    @Test
    fun `공개 전 DRAFT 요청은 존재를 노출하지 않고 404를 던진다`() {
        given(requestRepository.findByIdAndDeletedAtIsNullForUpdate(REQUEST_ID))
            .willReturn(request(PortfolioRequestStatus.DRAFT, dueAt = FUTURE))

        assertThatThrownBy { service.submit(REQUEST_ID, REQUESTER_ID, upsert(PortfolioSubmissionStatus.SUBMITTED)) }
            .isInstanceOf(PortfolioRequestNotFoundException::class.java)
        // 대상 여부조차 조회하지 않아야 "그 요청이 존재한다"는 사실이 새어 나가지 않는다.
        verify(targetRepository, never()).existsByRequestIdAndStudentMemberId(anyLong(), anyLong())
    }

    @Test
    fun `없거나 삭제된 요청은 404를 던진다`() {
        given(requestRepository.findByIdAndDeletedAtIsNullForUpdate(REQUEST_ID)).willReturn(null)

        assertThatThrownBy { service.submit(REQUEST_ID, REQUESTER_ID, upsert(PortfolioSubmissionStatus.SUBMITTED)) }
            .isInstanceOf(PortfolioRequestNotFoundException::class.java)
    }

    @Test
    fun `마감된 CLOSED 요청은 SUBMISSION_CLOSED를 던진다`() {
        given(requestRepository.findByIdAndDeletedAtIsNullForUpdate(REQUEST_ID))
            .willReturn(request(PortfolioRequestStatus.CLOSED, dueAt = FUTURE))
        givenIsTarget()

        assertThatThrownBy { service.submit(REQUEST_ID, REQUESTER_ID, upsert(PortfolioSubmissionStatus.SUBMITTED)) }
            .isInstanceOf(SubmissionClosedException::class.java)
        verify(submissionRepository, never()).saveAndFlush(any())
    }

    @Test
    fun `마감 시각을 지난 PUBLISHED 요청은 SUBMISSION_CLOSED를 던진다`() {
        given(requestRepository.findByIdAndDeletedAtIsNullForUpdate(REQUEST_ID))
            .willReturn(request(PortfolioRequestStatus.PUBLISHED, dueAt = PAST))
        givenIsTarget()

        assertThatThrownBy { service.submit(REQUEST_ID, REQUESTER_ID, upsert(PortfolioSubmissionStatus.SUBMITTED)) }
            .isInstanceOf(SubmissionClosedException::class.java)
    }

    @Test
    fun `기존 제출물이 있으면 새로 만들지 않고 덮어쓰며 최초 제출 시각을 유지한다`() {
        givenPublishedRequest()
        givenIsTarget()
        val firstSubmittedAt = LocalDateTime.of(2026, 9, 1, 9, 0)
        val existing =
            PortfolioSubmission(REQUEST_ID, REQUESTER_ID, PortfolioSubmissionStatus.SUBMITTED).apply {
                id = 55L
                submittedAt = firstSubmittedAt
            }
        given(submissionRepository.findByRequestIdAndMemberId(REQUEST_ID, REQUESTER_ID)).willReturn(existing)
        given(submissionRepository.saveAndFlush(any())).willAnswer { it.arguments[0] as PortfolioSubmission }
        given(fileLinkPort.linkedFilesOf(FileOwnerType.PORTFOLIO_SUBMISSION, 55L)).willReturn(emptyList())

        val response =
            service.submit(
                REQUEST_ID,
                REQUESTER_ID,
                upsert(PortfolioSubmissionStatus.SUBMITTED, portfolioUrl = "https://new"),
            )

        assertThat(response.submissionId).isEqualTo(55L)
        assertThat(response.portfolioUrl).isEqualTo("https://new")
        assertThat(response.submittedAt).isEqualTo(firstSubmittedAt)
    }

    @Test
    fun `원하는 파일 집합이 이미 연결된 집합과 같으면 재연결하지 않는다`() {
        givenPublishedRequest()
        givenIsTarget()
        givenNoExistingSubmission()
        givenSaveAssignsId(70L)
        given(fileLinkPort.linkedFilesOf(FileOwnerType.PORTFOLIO_SUBMISSION, 70L))
            .willReturn(listOf(snapshot(1L)))
        given(fileLinkPort.snapshotsOf(listOf(1L))).willReturn(mapOf(1L to snapshot(1L)))

        val response =
            service.submit(REQUEST_ID, REQUESTER_ID, upsert(PortfolioSubmissionStatus.SUBMITTED, fileIds = listOf(1L)))

        assertThat(response.files.map { it.fileId }).containsExactly(1L)
        verify(fileLinkPort, never()).unlinkAllOf(FileOwnerType.PORTFOLIO_SUBMISSION, 70L)
        verify(fileLinkPort, never()).validateAndLink(REQUESTER_ID, listOf(1L), FilePurpose.PORTFOLIO, 70L)
    }

    @Test
    fun `파일 집합이 바뀌면 전체 해제 후 다시 연결한다`() {
        givenPublishedRequest()
        givenIsTarget()
        givenNoExistingSubmission()
        givenSaveAssignsId(80L)
        given(fileLinkPort.linkedFilesOf(FileOwnerType.PORTFOLIO_SUBMISSION, 80L))
            .willReturn(listOf(snapshot(1L)))
        given(fileLinkPort.validateAndLink(REQUESTER_ID, listOf(2L), FilePurpose.PORTFOLIO, 80L))
            .willReturn(listOf(snapshot(2L)))

        val response =
            service.submit(REQUEST_ID, REQUESTER_ID, upsert(PortfolioSubmissionStatus.SUBMITTED, fileIds = listOf(2L)))

        assertThat(response.files.map { it.fileId }).containsExactly(2L)
        verify(fileLinkPort).unlinkAllOf(FileOwnerType.PORTFOLIO_SUBMISSION, 80L)
    }

    // --- helpers ---

    private fun givenPublishedRequest() {
        given(requestRepository.findByIdAndDeletedAtIsNullForUpdate(REQUEST_ID))
            .willReturn(request(PortfolioRequestStatus.PUBLISHED, dueAt = FUTURE))
    }

    private fun givenIsTarget() {
        given(targetRepository.existsByRequestIdAndStudentMemberId(REQUEST_ID, REQUESTER_ID)).willReturn(true)
    }

    private fun givenNoExistingSubmission() {
        given(submissionRepository.findByRequestIdAndMemberId(REQUEST_ID, REQUESTER_ID)).willReturn(null)
    }

    private fun givenSaveAssignsId(id: Long) {
        given(submissionRepository.saveAndFlush(any())).willAnswer {
            (it.arguments[0] as PortfolioSubmission).apply { this.id = id }
        }
    }

    private fun request(
        status: PortfolioRequestStatus,
        dueAt: LocalDateTime,
    ) = PortfolioRequest(
        createdByMemberId = 9L,
        title = "2026 상반기 포트폴리오 수합",
        dueAt = dueAt,
        status = status,
    ).apply { id = REQUEST_ID }

    private fun upsert(
        status: PortfolioSubmissionStatus,
        portfolioUrl: String? = null,
        note: String? = null,
        fileIds: List<Long> = emptyList(),
    ) = PortfolioSubmissionUpsertRequest(
        status = status,
        portfolioUrl = portfolioUrl,
        note = note,
        fileIds = fileIds,
    )

    private fun snapshot(fileId: Long) =
        FileSnapshot(fileId = fileId, originalName = "file$fileId.pdf", contentType = "application/pdf", size = 1024L)

    companion object {
        private const val REQUEST_ID = 1L
        private const val REQUESTER_ID = 7L
        private val FUTURE: LocalDateTime = LocalDateTime.now().plusDays(1)
        private val PAST: LocalDateTime = LocalDateTime.now().minusDays(1)
    }
}
