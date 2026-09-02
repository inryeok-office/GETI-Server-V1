package team.inreok.getiserver.domain.portfolio.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyIterable
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import team.inreok.getiserver.domain.member.query.PortfolioTargetMemberQueryPort
import team.inreok.getiserver.domain.portfolio.dto.PortfolioRequestCreateRequest
import team.inreok.getiserver.domain.portfolio.dto.PortfolioRequestStatusUpdateRequest
import team.inreok.getiserver.domain.portfolio.dto.PortfolioRequestUpdateRequest
import team.inreok.getiserver.domain.portfolio.entity.PortfolioRequest
import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioRequestStatus
import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioSubmissionStatus
import team.inreok.getiserver.domain.portfolio.exception.InvalidTargetStudentException
import team.inreok.getiserver.domain.portfolio.exception.NotRequestTargetException
import team.inreok.getiserver.domain.portfolio.exception.PortfolioRequestNotEditableException
import team.inreok.getiserver.domain.portfolio.exception.PortfolioRequestNotFoundException
import team.inreok.getiserver.domain.portfolio.exception.PortfolioStatusTransitionInvalidException
import team.inreok.getiserver.domain.portfolio.exception.TargetStudentRequiredException
import team.inreok.getiserver.domain.portfolio.repository.PortfolioRequestRepository
import team.inreok.getiserver.domain.portfolio.repository.PortfolioRequestTargetRepository
import team.inreok.getiserver.domain.portfolio.repository.PortfolioSubmissionRepository
import team.inreok.getiserver.domain.portfolio.repository.RequestCountProjection
import team.inreok.getiserver.domain.portfolio.service.impl.PortfolioRequestServiceImpl
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class PortfolioRequestServiceTest {
    @Mock
    private lateinit var requestRepository: PortfolioRequestRepository

    @Mock
    private lateinit var targetRepository: PortfolioRequestTargetRepository

    @Mock
    private lateinit var submissionRepository: PortfolioSubmissionRepository

    @Mock
    private lateinit var targetMemberQueryPort: PortfolioTargetMemberQueryPort

    private val service by lazy {
        PortfolioRequestServiceImpl(requestRepository, targetRepository, submissionRepository, targetMemberQueryPort)
    }

    // --- create ---

    @Test
    fun `유효한 대상으로 수합 요청을 등록하면 DRAFT로 저장하고 대상과 개수를 반영한다`() {
        givenAllStudents(setOf(1L, 2L, 3L))
        givenSaveAssignsId(10L)
        given(targetRepository.countByRequestId(10L)).willReturn(3L)
        given(submissionRepository.countByRequestIdAndStatus(10L, PortfolioSubmissionStatus.SUBMITTED)).willReturn(0L)

        val response = service.create(createRequest(targetStudentIds = listOf(1L, 2L, 3L)), REQUESTER_ID)

        assertThat(response.requestId).isEqualTo(10L)
        assertThat(response.status).isEqualTo(PortfolioRequestStatus.DRAFT)
        assertThat(response.targetCount).isEqualTo(3L)
        assertThat(response.submittedCount).isEqualTo(0L)
        verify(targetRepository).saveAll(anyIterable())
    }

    @Test
    fun `대상 학생이 비어 있으면 등록을 거부한다`() {
        assertThatThrownBy { service.create(createRequest(targetStudentIds = emptyList()), REQUESTER_ID) }
            .isInstanceOf(TargetStudentRequiredException::class.java)
        verify(requestRepository, never()).saveAndFlush(any())
    }

    @Test
    fun `대상 중 존재하지 않거나 학생이 아닌 id가 있으면 등록을 거부한다`() {
        // 3명을 요청했지만 2명만 STUDENT로 확인된다.
        given(targetMemberQueryPort.findStudentMemberIds(setOf(1L, 2L, 3L))).willReturn(setOf(1L, 2L))

        assertThatThrownBy { service.create(createRequest(targetStudentIds = listOf(1L, 2L, 3L)), REQUESTER_ID) }
            .isInstanceOf(InvalidTargetStudentException::class.java)
        verify(requestRepository, never()).saveAndFlush(any())
    }

    @Test
    fun `중복 대상 id는 하나로 취급해 검증한다`() {
        given(targetMemberQueryPort.findStudentMemberIds(setOf(1L, 2L))).willReturn(setOf(1L, 2L))
        givenSaveAssignsId(10L)
        given(targetRepository.countByRequestId(10L)).willReturn(2L)
        given(submissionRepository.countByRequestIdAndStatus(10L, PortfolioSubmissionStatus.SUBMITTED)).willReturn(0L)

        val response = service.create(createRequest(targetStudentIds = listOf(1L, 2L, 2L, 1L)), REQUESTER_ID)

        assertThat(response.targetCount).isEqualTo(2L)
    }

    // --- update ---

    @Test
    fun `전달하지 않은 Field는 기존 값을 유지한다`() {
        val entity = requestOf(id = 5L, status = PortfolioRequestStatus.DRAFT, title = "원래 제목")
        given(requestRepository.findByIdAndDeletedAtIsNull(5L)).willReturn(entity)
        givenCounts(5L, target = 1L)

        val response = service.update(5L, PortfolioRequestUpdateRequest(dueAt = LocalDateTime.of(2026, 10, 1, 0, 0)))

        assertThat(response.title).isEqualTo("원래 제목")
        assertThat(entity.dueAt).isEqualTo(LocalDateTime.of(2026, 10, 1, 0, 0))
    }

    @Test
    fun `CLOSED 요청은 수정할 수 없다`() {
        given(requestRepository.findByIdAndDeletedAtIsNull(5L))
            .willReturn(requestOf(id = 5L, status = PortfolioRequestStatus.CLOSED))

        assertThatThrownBy { service.update(5L, PortfolioRequestUpdateRequest(title = "새 제목")) }
            .isInstanceOf(PortfolioRequestNotEditableException::class.java)
    }

    @Test
    fun `PUBLISHED 상태에서 대상 교체를 시도하면 거부한다`() {
        given(requestRepository.findByIdAndDeletedAtIsNull(5L))
            .willReturn(requestOf(id = 5L, status = PortfolioRequestStatus.PUBLISHED))

        assertThatThrownBy { service.update(5L, PortfolioRequestUpdateRequest(targetStudentIds = listOf(1L))) }
            .isInstanceOf(PortfolioRequestNotEditableException::class.java)
        verify(targetRepository, never()).deleteByRequestId(anyLong())
    }

    @Test
    fun `없는 요청을 수정하면 404를 던진다`() {
        given(requestRepository.findByIdAndDeletedAtIsNull(99L)).willReturn(null)

        assertThatThrownBy { service.update(99L, PortfolioRequestUpdateRequest(title = "x")) }
            .isInstanceOf(PortfolioRequestNotFoundException::class.java)
    }

    // --- changeStatus ---

    @Test
    fun `대상이 있으면 DRAFT에서 PUBLISHED로 공개할 수 있다`() {
        val entity = requestOf(id = 5L, status = PortfolioRequestStatus.DRAFT)
        given(requestRepository.findByIdAndDeletedAtIsNull(5L)).willReturn(entity)
        given(targetRepository.countByRequestId(5L)).willReturn(3L)
        given(submissionRepository.countByRequestIdAndStatus(5L, PortfolioSubmissionStatus.SUBMITTED)).willReturn(0L)

        val response = service.changeStatus(5L, PortfolioRequestStatusUpdateRequest(PortfolioRequestStatus.PUBLISHED))

        assertThat(response.status).isEqualTo(PortfolioRequestStatus.PUBLISHED)
        assertThat(entity.status).isEqualTo(PortfolioRequestStatus.PUBLISHED)
    }

    @Test
    fun `대상이 없으면 공개할 수 없다`() {
        given(requestRepository.findByIdAndDeletedAtIsNull(5L))
            .willReturn(requestOf(id = 5L, status = PortfolioRequestStatus.DRAFT))
        given(targetRepository.countByRequestId(5L)).willReturn(0L)

        assertThatThrownBy {
            service.changeStatus(5L, PortfolioRequestStatusUpdateRequest(PortfolioRequestStatus.PUBLISHED))
        }.isInstanceOf(TargetStudentRequiredException::class.java)
    }

    @Test
    fun `허용되지 않은 상태 전이는 거부한다`() {
        given(requestRepository.findByIdAndDeletedAtIsNull(5L))
            .willReturn(requestOf(id = 5L, status = PortfolioRequestStatus.DRAFT))

        assertThatThrownBy {
            service.changeStatus(5L, PortfolioRequestStatusUpdateRequest(PortfolioRequestStatus.CLOSED))
        }.isInstanceOf(PortfolioStatusTransitionInvalidException::class.java)
    }

    @Test
    fun `삭제는 Soft Delete로 처리해 deletedAt을 남긴다`() {
        val entity = requestOf(id = 5L, status = PortfolioRequestStatus.PUBLISHED)
        given(requestRepository.findByIdAndDeletedAtIsNull(5L)).willReturn(entity)
        givenCounts(5L, target = 1L)

        service.changeStatus(5L, PortfolioRequestStatusUpdateRequest(PortfolioRequestStatus.DELETED))

        assertThat(entity.status).isEqualTo(PortfolioRequestStatus.DELETED)
        assertThat(entity.deletedAt).isNotNull()
    }

    // --- getDetail ---

    @Test
    fun `학생이 대상이 아니면 상세 조회 시 403을 던진다`() {
        given(requestRepository.findByIdAndDeletedAtIsNull(5L))
            .willReturn(requestOf(id = 5L, status = PortfolioRequestStatus.PUBLISHED))
        given(targetRepository.existsByRequestIdAndStudentMemberId(5L, REQUESTER_ID)).willReturn(false)

        assertThatThrownBy { service.getDetail(5L, REQUESTER_ID, isAdmin = false) }
            .isInstanceOf(NotRequestTargetException::class.java)
    }

    @Test
    fun `학생은 아직 공개되지 않은 DRAFT 요청을 볼 수 없다`() {
        given(requestRepository.findByIdAndDeletedAtIsNull(5L))
            .willReturn(requestOf(id = 5L, status = PortfolioRequestStatus.DRAFT))

        assertThatThrownBy { service.getDetail(5L, REQUESTER_ID, isAdmin = false) }
            .isInstanceOf(PortfolioRequestNotFoundException::class.java)
        // DRAFT는 소유권 검사보다 먼저 404로 처리되므로 대상 여부(existsBy)를 조회하지 않는다.
        verify(targetRepository, never()).existsByRequestIdAndStudentMemberId(anyLong(), anyLong())
    }

    @Test
    fun `대상 학생은 공개된 요청 상세를 조회할 수 있다`() {
        given(requestRepository.findByIdAndDeletedAtIsNull(5L))
            .willReturn(requestOf(id = 5L, status = PortfolioRequestStatus.PUBLISHED))
        given(targetRepository.existsByRequestIdAndStudentMemberId(5L, REQUESTER_ID)).willReturn(true)
        givenCounts(5L, target = 3L, submitted = 1L)

        val response = service.getDetail(5L, REQUESTER_ID, isAdmin = false)

        assertThat(response.requestId).isEqualTo(5L)
        assertThat(response.submittedCount).isEqualTo(1L)
    }

    @Test
    fun `관리자는 DRAFT 요청 상세도 대상 검증 없이 조회할 수 있다`() {
        given(requestRepository.findByIdAndDeletedAtIsNull(5L))
            .willReturn(requestOf(id = 5L, status = PortfolioRequestStatus.DRAFT))
        givenCounts(5L, target = 3L)

        val response = service.getDetail(5L, REQUESTER_ID, isAdmin = true)

        assertThat(response.status).isEqualTo(PortfolioRequestStatus.DRAFT)
        verify(targetRepository, never()).existsByRequestIdAndStudentMemberId(anyLong(), anyLong())
    }

    @Test
    fun `대상이 아닌 학생도 DRAFT 요청은 404를 받아 존재가 노출되지 않는다`() {
        // DRAFT는 대상 여부보다 먼저 404로 처리되어야 한다 -- 대상이 아닌 학생이 실제 존재하는
        // DRAFT 요청 id를 조회했을 때 403(NOT_REQUEST_TARGET)으로 존재가 드러나면 안 된다.
        given(requestRepository.findByIdAndDeletedAtIsNull(5L))
            .willReturn(requestOf(id = 5L, status = PortfolioRequestStatus.DRAFT))

        assertThatThrownBy { service.getDetail(5L, REQUESTER_ID, isAdmin = false) }
            .isInstanceOf(PortfolioRequestNotFoundException::class.java)
        verify(targetRepository, never()).existsByRequestIdAndStudentMemberId(anyLong(), anyLong())
    }

    // --- getList ---

    @Test
    fun `목록 조회 - 학생이면 자신이 대상인 공개 이후 요청만 조회하고 개수를 매핑한다`() {
        val pageable = PageRequest.of(0, 20)
        val published = requestOf(id = 5L, status = PortfolioRequestStatus.PUBLISHED)
        val closed = requestOf(id = 6L, status = PortfolioRequestStatus.CLOSED)
        given(requestRepository.findAllForStudent(anyLong(), anyList(), any(), any() ?: pageable))
            .willReturn(PageImpl(listOf(published, closed), pageable, 2))
        // 5L만 집계 결과가 있고 6L은 없다 -- 없는 요청은 0으로 취급되어야 한다.
        given(targetRepository.countGroupedByRequestId(listOf(5L, 6L))).willReturn(listOf(countRow(5L, 3L)))
        given(
            submissionRepository.countGroupedByRequestIdAndStatus(listOf(5L, 6L), PortfolioSubmissionStatus.SUBMITTED),
        ).willReturn(listOf(countRow(5L, 1L)))

        val result = service.getList(status = null, requesterId = REQUESTER_ID, isAdmin = false, pageable = pageable)

        assertThat(result.content).hasSize(2)
        assertThat(result.content[0].targetCount).isEqualTo(3L)
        assertThat(result.content[0].submittedCount).isEqualTo(1L)
        assertThat(result.content[1].targetCount).isEqualTo(0L)
        assertThat(result.content[1].submittedCount).isEqualTo(0L)
        // 학생 목록은 자신이 대상인 공개 이후(PUBLISHED/CLOSED) 요청만 조회해야 한다. 관리자용
        // findAllForAdmin으로 새면 DRAFT·타 학생 대상까지 노출되므로, 학생 경로 호출과 가시 상태(그
        // 자체가 대상 필터의 핵심)를 함께 검증한다.
        @Suppress("UNCHECKED_CAST")
        val statusCaptor =
            ArgumentCaptor.forClass(Collection::class.java) as ArgumentCaptor<Collection<PortfolioRequestStatus>>
        verify(requestRepository)
            .findAllForStudent(anyLong(), statusCaptor.capture() ?: emptyList(), any(), any() ?: pageable)
        assertThat(statusCaptor.value)
            .containsExactly(PortfolioRequestStatus.PUBLISHED, PortfolioRequestStatus.CLOSED)
        verify(requestRepository, never()).findAllForAdmin(any(), any() ?: pageable)
    }

    @Test
    fun `목록 조회 - 관리자면 전체 요청을 조회한다`() {
        val pageable = PageRequest.of(0, 20)
        val draft = requestOf(id = 7L, status = PortfolioRequestStatus.DRAFT)
        given(requestRepository.findAllForAdmin(any(), any() ?: pageable))
            .willReturn(PageImpl(listOf(draft), pageable, 1))
        given(targetRepository.countGroupedByRequestId(listOf(7L))).willReturn(listOf(countRow(7L, 2L)))
        given(submissionRepository.countGroupedByRequestIdAndStatus(listOf(7L), PortfolioSubmissionStatus.SUBMITTED))
            .willReturn(emptyList())

        val result = service.getList(status = null, requesterId = REQUESTER_ID, isAdmin = true, pageable = pageable)

        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].status).isEqualTo(PortfolioRequestStatus.DRAFT)
        assertThat(result.content[0].targetCount).isEqualTo(2L)
        verify(requestRepository, never()).findAllForStudent(anyLong(), anyList(), any(), any() ?: pageable)
    }

    // --- helpers ---

    private fun countRow(
        requestId: Long,
        count: Long,
    ): RequestCountProjection =
        object : RequestCountProjection {
            override val requestId = requestId
            override val count = count
        }

    private fun givenAllStudents(ids: Set<Long>) {
        given(targetMemberQueryPort.findStudentMemberIds(ids)).willReturn(ids)
    }

    private fun givenSaveAssignsId(id: Long) {
        given(requestRepository.saveAndFlush(any())).willAnswer {
            (it.arguments[0] as PortfolioRequest).apply { this.id = id }
        }
    }

    private fun givenCounts(
        requestId: Long,
        target: Long = 0L,
        submitted: Long = 0L,
    ) {
        given(targetRepository.countByRequestId(requestId)).willReturn(target)
        given(submissionRepository.countByRequestIdAndStatus(requestId, PortfolioSubmissionStatus.SUBMITTED))
            .willReturn(submitted)
    }

    private fun createRequest(targetStudentIds: List<Long>) =
        PortfolioRequestCreateRequest(
            title = "포트폴리오 수합",
            description = "설명",
            dueAt = LocalDateTime.of(2026, 9, 30, 23, 59),
            targetStudentIds = targetStudentIds,
        )

    private fun requestOf(
        id: Long,
        status: PortfolioRequestStatus,
        title: String = "제목",
    ) = PortfolioRequest(
        createdByMemberId = REQUESTER_ID,
        title = title,
        dueAt = LocalDateTime.of(2026, 9, 30, 23, 59),
        status = status,
    ).apply { this.id = id }

    private companion object {
        private const val REQUESTER_ID = 7L
    }
}
