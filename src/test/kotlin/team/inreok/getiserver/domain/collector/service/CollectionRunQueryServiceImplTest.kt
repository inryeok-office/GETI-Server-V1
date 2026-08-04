package team.inreok.getiserver.domain.collector.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import team.inreok.getiserver.domain.collector.entity.CollectionRun
import team.inreok.getiserver.domain.collector.entity.CollectionRunError
import team.inreok.getiserver.domain.collector.entity.JobSource
import team.inreok.getiserver.domain.collector.entity.type.CollectionRunStatus
import team.inreok.getiserver.domain.collector.entity.type.CollectorAction
import team.inreok.getiserver.domain.collector.entity.type.JobSourceApprovalStatus
import team.inreok.getiserver.domain.collector.entity.type.JobSourceCode
import team.inreok.getiserver.domain.collector.entity.type.JobSourceType
import team.inreok.getiserver.domain.collector.exception.CollectionRunNotFoundException
import team.inreok.getiserver.domain.collector.repository.CollectionRunErrorRepository
import team.inreok.getiserver.domain.collector.repository.CollectionRunRepository
import team.inreok.getiserver.domain.collector.repository.JobSourceRepository
import team.inreok.getiserver.domain.collector.service.impl.CollectionRunQueryServiceImpl
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class CollectionRunQueryServiceImplTest {
    @Mock
    private lateinit var collectionRunRepository: CollectionRunRepository

    @Mock
    private lateinit var collectionRunErrorRepository: CollectionRunErrorRepository

    @Mock
    private lateinit var jobSourceRepository: JobSourceRepository

    private val service: CollectionRunQueryService by lazy {
        CollectionRunQueryServiceImpl(collectionRunRepository, collectionRunErrorRepository, jobSourceRepository)
    }

    private val now = LocalDateTime.of(2026, 8, 3, 9, 0)

    private fun sourceOf(
        id: Long,
        name: String,
    ) = JobSource(
        sourceCode = JobSourceCode.MMA,
        name = name,
        sourceType = JobSourceType.EXTERNAL_API,
        approvalStatus = JobSourceApprovalStatus.READY,
    ).apply { this.id = id }

    private fun runOf(
        id: Long,
        sourceId: Long,
        status: CollectionRunStatus = CollectionRunStatus.SUCCESS,
    ) = CollectionRun(sourceId = sourceId, action = CollectorAction.SYNC, status = status, startedAt = now).apply {
        this.id =
            id
    }

    @Test
    fun `목록 조회는 N+1 없이 수집원 이름을 채워 반환한다`() {
        val page = PageImpl(listOf(runOf(id = 1L, sourceId = 10L)), PageRequest.of(0, 20), 1)
        given(collectionRunRepository.search(null, null, null, null, PageRequest.of(0, 20))).willReturn(page)
        given(jobSourceRepository.findAllById(listOf(10L))).willReturn(listOf(sourceOf(10L, "병역일터")))

        val result = service.search(null, null, null, null, PageRequest.of(0, 20))

        assertThat(result.content.single().sourceName).isEqualTo("병역일터")
        assertThat(result.totalElements).isEqualTo(1)
    }

    @Test
    fun `존재하지 않는 수집원 ID는 알 수 없음으로 채워진다`() {
        val page = PageImpl(listOf(runOf(id = 1L, sourceId = 999L)), PageRequest.of(0, 20), 1)
        given(collectionRunRepository.search(null, null, null, null, PageRequest.of(0, 20))).willReturn(page)
        given(jobSourceRepository.findAllById(listOf(999L))).willReturn(emptyList())

        val result = service.search(null, null, null, null, PageRequest.of(0, 20))

        assertThat(result.content.single().sourceName).isEqualTo("알 수 없음")
    }

    @Test
    fun `상세 조회는 발생 순서대로 정렬된 오류 목록을 포함한다`() {
        val run = runOf(id = 5L, sourceId = 10L, status = CollectionRunStatus.PARTIAL_SUCCESS)
        given(collectionRunRepository.findById(5L)).willReturn(Optional.of(run))
        given(jobSourceRepository.findAllById(listOf(10L))).willReturn(listOf(sourceOf(10L, "병역일터")))
        given(collectionRunErrorRepository.findAllByRunIdOrderByOccurredAtAsc(5L)).willReturn(
            listOf(
                CollectionRunError(runId = 5L, code = "COLLECTOR_PROVIDER_TIMEOUT", message = "시간 초과", occurredAt = now)
                    .apply { missingFields = "title,externalUrl" },
            ),
        )

        val result = service.getDetail(5L)

        assertThat(result.sourceName).isEqualTo("병역일터")
        assertThat(result.errors.single().missingFields).containsExactly("title", "externalUrl")
    }

    @Test
    fun `존재하지 않는 수집 실행을 조회하면 CollectionRunNotFoundException이 발생한다`() {
        given(collectionRunRepository.findById(999L)).willReturn(Optional.empty())

        assertThatThrownBy { service.getDetail(999L) }
            .isInstanceOf(CollectionRunNotFoundException::class.java)
    }
}
