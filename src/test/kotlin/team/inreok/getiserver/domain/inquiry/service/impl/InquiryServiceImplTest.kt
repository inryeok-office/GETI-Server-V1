package team.inreok.getiserver.domain.inquiry.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.file.link.FileLinkPort
import team.inreok.getiserver.domain.file.link.FileSnapshot
import team.inreok.getiserver.domain.file.link.FileUrlPort
import team.inreok.getiserver.domain.inquiry.dto.InquiryAnswerCreateRequest
import team.inreok.getiserver.domain.inquiry.dto.InquiryAssigneeUpdateRequest
import team.inreok.getiserver.domain.inquiry.dto.InquiryCreateRequest
import team.inreok.getiserver.domain.inquiry.dto.InquiryStatusUpdateRequest
import team.inreok.getiserver.domain.inquiry.entity.Inquiry
import team.inreok.getiserver.domain.inquiry.entity.InquiryAnswer
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryStatus
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryType
import team.inreok.getiserver.domain.inquiry.exception.InquiryStatusInvalidException
import team.inreok.getiserver.domain.inquiry.exception.InvalidInquiryAssigneeException
import team.inreok.getiserver.domain.inquiry.repository.InquiryAnswerRepository
import team.inreok.getiserver.domain.inquiry.repository.InquiryRepository
import team.inreok.getiserver.domain.inquiry.service.InquiryService
import team.inreok.getiserver.domain.member.query.InquiryAssigneeCandidate
import team.inreok.getiserver.domain.member.query.InquiryAssigneeCandidateQueryPort
import team.inreok.getiserver.domain.member.query.InquiryAuthorSearchQueryPort
import team.inreok.getiserver.domain.member.query.InquiryMemberSnapshotQueryPort
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InquiryServiceImplTest {
    @Mock
    private lateinit var inquiryRepository: InquiryRepository

    @Mock
    private lateinit var inquiryAnswerRepository: InquiryAnswerRepository

    @Mock
    private lateinit var inquiryMemberSnapshotQueryPort: InquiryMemberSnapshotQueryPort

    @Mock
    private lateinit var inquiryAssigneeCandidateQueryPort: InquiryAssigneeCandidateQueryPort

    @Mock
    private lateinit var inquiryAuthorSearchQueryPort: InquiryAuthorSearchQueryPort

    @Mock
    private lateinit var fileLinkPort: FileLinkPort

    @Mock
    private lateinit var fileUrlPort: FileUrlPort

    @Mock
    private lateinit var eventPublisher: ApplicationEventPublisher

    private val service: InquiryService by lazy {
        InquiryServiceImpl(
            inquiryRepository,
            inquiryAnswerRepository,
            inquiryMemberSnapshotQueryPort,
            inquiryAssigneeCandidateQueryPort,
            inquiryAuthorSearchQueryPort,
            fileLinkPort,
            fileUrlPort,
            eventPublisher,
        )
    }

    private val now = LocalDateTime.of(2026, 8, 9, 12, 0, 0)

    // --- 상세 조회: assignee 노출/숨김(§84 29번) ---

    @Test
    fun `개발자가 아니면 담당자가 지정돼 있어도 assignee를 노출하지 않는다`() {
        given(inquiryRepository.findById(1L)).willReturn(Optional.of(inquiryOf(assigneeMemberId = 5L)))

        val response = service.getDetail(1L, requesterMemberId = 1L, isDeveloper = false)

        assertThat(response.assignee).isNull()
    }

    @Test
    fun `개발자면 지정된 담당자가 노출된다`() {
        given(
            inquiryRepository.findById(1L),
        ).willReturn(Optional.of(inquiryOf(authorMemberId = 2L, assigneeMemberId = 5L)))

        val response = service.getDetail(1L, requesterMemberId = 9L, isDeveloper = true)

        assertThat(response.assignee?.memberId).isEqualTo(5L)
    }

    // --- 상세 조회: 답변 첨부파일 배치 조회(N+1 방지) ---

    @Test
    fun `상세 조회는 배치로 받은 답변 첨부파일을 answerId별로 매칭한다`() {
        given(inquiryRepository.findById(1L)).willReturn(Optional.of(inquiryOf()))
        val firstAnswer = answerOf(id = 10L, content = "첫 번째 답변")
        val secondAnswer = answerOf(id = 20L, content = "두 번째 답변")
        given(inquiryAnswerRepository.findAllByInquiryIdOrderByCreatedAtAscIdAsc(1L))
            .willReturn(listOf(firstAnswer, secondAnswer))
        given(fileLinkPort.linkedFilesOf(FileOwnerType.INQUIRY_ANSWER, listOf(10L, 20L)))
            .willReturn(mapOf(20L to listOf(fileSnapshotOf(fileId = 99L))))

        val response = service.getDetail(1L, requesterMemberId = 1L, isDeveloper = false)

        assertThat(response.answers).hasSize(2)
        assertThat(response.answers[0].files).isEmpty()
        assertThat(
            response.answers[1]
                .files
                .single()
                .fileId,
        ).isEqualTo(99L)
    }

    // --- 상태 전이 화이트리스트 ---

    @Test
    fun `RECEIVED에서 IN_PROGRESS로는 전이가 허용된다`() {
        given(inquiryRepository.findByIdForUpdate(1L)).willReturn(inquiryOf(status = InquiryStatus.RECEIVED))

        val response = service.changeStatus(1L, InquiryStatusUpdateRequest(status = InquiryStatus.IN_PROGRESS))

        assertThat(response.status).isEqualTo(InquiryStatus.IN_PROGRESS)
    }

    @Test
    fun `RECEIVED에서 CLOSED로의 직접 전이는 거부된다`() {
        given(inquiryRepository.findByIdForUpdate(1L)).willReturn(inquiryOf(status = InquiryStatus.RECEIVED))

        assertThatThrownBy {
            service.changeStatus(1L, InquiryStatusUpdateRequest(status = InquiryStatus.CLOSED))
        }.isInstanceOf(InquiryStatusInvalidException::class.java)
    }

    // status=ANSWERED를 직접 지정할 때 답변이 없으면 거부한다. RECEIVED->ANSWERED 자체는 전이
    // Matrix상 허용되므로(ALLOWED_TRANSITIONS), 이 Test는 전이 Matrix가 아니라 그 다음
    // 검증(답변 존재 여부)이 실제로 실행되는지를 확인한다.
    @Test
    fun `답변이 없는 문의를 ANSWERED로 직접 변경하면 거부된다`() {
        given(inquiryRepository.findByIdForUpdate(1L)).willReturn(inquiryOf(status = InquiryStatus.RECEIVED))
        given(inquiryAnswerRepository.existsByInquiryId(1L)).willReturn(false)

        assertThatThrownBy {
            service.changeStatus(1L, InquiryStatusUpdateRequest(status = InquiryStatus.ANSWERED))
        }.isInstanceOf(InquiryStatusInvalidException::class.java)
    }

    // --- 담당자 자격 판정: role=DEVELOPER AND status=ACTIVE ---

    @Test
    fun `role=DEVELOPER이고 status=ACTIVE인 회원은 담당자로 지정할 수 있다`() {
        given(inquiryRepository.findById(1L)).willReturn(Optional.of(inquiryOf()))
        given(inquiryAssigneeCandidateQueryPort.findById(5L)).willReturn(assigneeCandidateOf("DEVELOPER", "ACTIVE"))

        val response = service.updateAssignee(1L, InquiryAssigneeUpdateRequest(assigneeId = 5L))

        assertThat(response.assignee?.memberId).isEqualTo(5L)
    }

    @Test
    fun `role=DEVELOPER이지만 status가 ACTIVE가 아니면 담당자 지정을 거부한다`() {
        given(inquiryRepository.findById(1L)).willReturn(Optional.of(inquiryOf()))
        given(inquiryAssigneeCandidateQueryPort.findById(5L)).willReturn(assigneeCandidateOf("DEVELOPER", "PENDING"))

        assertThatThrownBy {
            service.updateAssignee(1L, InquiryAssigneeUpdateRequest(assigneeId = 5L))
        }.isInstanceOf(InvalidInquiryAssigneeException::class.java)
    }

    @Test
    fun `status가 ACTIVE여도 role=DEVELOPER가 아니면 담당자 지정을 거부한다`() {
        given(inquiryRepository.findById(1L)).willReturn(Optional.of(inquiryOf()))
        given(inquiryAssigneeCandidateQueryPort.findById(5L)).willReturn(assigneeCandidateOf("STUDENT", "ACTIVE"))

        assertThatThrownBy {
            service.updateAssignee(1L, InquiryAssigneeUpdateRequest(assigneeId = 5L))
        }.isInstanceOf(InvalidInquiryAssigneeException::class.java)
    }

    // --- 답변 등록 ---

    @Test
    fun `이미 답변이 있던 문의에 새 답변이 등록돼도 answeredAt은 갱신되지 않는다`() {
        val originalAnsweredAt = now.minusDays(3)
        val inquiry = inquiryOf(status = InquiryStatus.IN_PROGRESS, answeredAt = originalAnsweredAt)
        given(inquiryRepository.existsById(1L)).willReturn(true)
        given(inquiryAnswerRepository.saveAndFlush(any(InquiryAnswer::class.java))).willAnswer { invocation ->
            (invocation.arguments[0] as InquiryAnswer).apply {
                id = 10L
                createdAt = now
            }
        }
        given(inquiryRepository.findByIdForUpdate(1L)).willReturn(inquiry)

        val response = service.createAnswer(1L, InquiryAnswerCreateRequest(content = "새 답변"), authorMemberId = 9L)

        assertThat(inquiry.answeredAt).isEqualTo(originalAnsweredAt)
        assertThat(response.inquiryStatus).isEqualTo(InquiryStatus.ANSWERED)
    }

    // --- 목록 조회(관리자): 작성자 이름 검색어 LIKE 이스케이프(AI 코드리뷰 P2) ---

    // 검색어에 %/_가 포함되면 이스케이프 없이 findIdsByNameContaining에 넘길 경우
    // MemberRepository.findIdsByNameContaining의 LIKE 계약을 어겨 Wildcard로 해석된다. 두 경로에
    // 같은 이스케이프 값이 전달되는지 검증한다.
    @Test
    fun `listAdmin은 검색어의 LIKE Wildcard를 이스케이프해 작성자 이름 검색과 목록 검색에 동일한 값으로 전달한다`() {
        val rawQuery = "50%_off"
        val escapedQuery = "50\\%\\_off"
        val authorIds = setOf(3L, 7L)
        val pageable = PageRequest.of(0, 20)
        given(inquiryAuthorSearchQueryPort.findIdsByNameContaining(escapedQuery)).willReturn(authorIds)
        given(
            inquiryRepository.searchForAdmin(
                type = null,
                status = null,
                assigneeId = null,
                mineOnlyMemberId = null,
                hasQuery = true,
                query = escapedQuery,
                authorIds = authorIds,
                pageable = pageable,
            ),
        ).willReturn(PageImpl(emptyList(), pageable, 0))

        service.listAdmin(
            inquiryType = null,
            status = null,
            query = rawQuery,
            assigneeId = null,
            mineOnly = false,
            requesterMemberId = 1L,
            pageable = pageable,
        )

        verify(inquiryAuthorSearchQueryPort).findIdsByNameContaining(escapedQuery)
        verify(inquiryRepository).searchForAdmin(
            type = null,
            status = null,
            assigneeId = null,
            mineOnlyMemberId = null,
            hasQuery = true,
            query = escapedQuery,
            authorIds = authorIds,
            pageable = pageable,
        )
    }

    // --- 등록: 초기 상태 ---

    @Test
    fun `문의를 등록하면 상태가 RECEIVED로 고정된다`() {
        given(inquiryRepository.saveAndFlush(any(Inquiry::class.java))).willAnswer { invocation ->
            (invocation.arguments[0] as Inquiry).apply {
                id = 1L
                createdAt = now
                updatedAt = now
            }
        }

        val response = service.create(createRequest(), authorMemberId = 1L)

        assertThat(response.status).isEqualTo(InquiryStatus.RECEIVED)
    }

    // --- Fixtures ---

    private fun inquiryOf(
        authorMemberId: Long = 1L,
        status: InquiryStatus = InquiryStatus.RECEIVED,
        assigneeMemberId: Long? = null,
        answeredAt: LocalDateTime? = null,
    ) = Inquiry(
        authorMemberId = authorMemberId,
        type = InquiryType.ETC,
        title = "문의 제목",
        content = "문의 내용",
        status = status,
    ).apply {
        id = 1L
        this.assigneeMemberId = assigneeMemberId
        this.answeredAt = answeredAt
        createdAt = now
        updatedAt = now
    }

    private fun answerOf(
        id: Long,
        content: String,
    ) = InquiryAnswer(inquiryId = 1L, authorMemberId = 9L, content = content).apply {
        this.id = id
        createdAt = now
    }

    private fun fileSnapshotOf(fileId: Long) =
        FileSnapshot(fileId = fileId, originalName = "file.png", contentType = "image/png", size = 100L)

    private fun assigneeCandidateOf(
        role: String,
        status: String,
    ) = InquiryAssigneeCandidate(memberId = 5L, name = "후보", roles = setOf(role), status = status)

    private fun createRequest() =
        InquiryCreateRequest(
            inquiryType = InquiryType.ETC,
            title = "문의 제목",
            content = "문의 내용",
        )
}
