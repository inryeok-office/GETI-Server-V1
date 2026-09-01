package team.inreok.getiserver.domain.search.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import team.inreok.getiserver.domain.search.config.SearchProperties
import team.inreok.getiserver.domain.search.document.JobSearchDocument
import team.inreok.getiserver.domain.search.entity.SearchIndexFailure
import team.inreok.getiserver.domain.search.entity.type.SearchIndexOperation
import team.inreok.getiserver.domain.search.index.JobSearchIndexManager
import team.inreok.getiserver.domain.search.repository.SearchIndexFailureRepository
import team.inreok.getiserver.domain.search.service.JobIndexDocumentBuilder
import java.time.LocalDateTime

/**
 * Strictness를 LENIENT로 둔 이유는 `JobServiceTest`와 같다 — 공통 Fixture가 Test마다 다르게
 * 쓰이는데, 앞선 검증 실패 Test는 뒤 Stub까지 도달하지 않기 때문이다.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobIndexSyncServiceImplTest {
    @Mock
    private lateinit var documentBuilder: JobIndexDocumentBuilder

    @Mock
    private lateinit var indexManager: JobSearchIndexManager

    @Mock
    private lateinit var failureRepository: SearchIndexFailureRepository

    @Captor
    private lateinit var failureCaptor: ArgumentCaptor<SearchIndexFailure>

    private val properties = SearchProperties(indexRetryMaxAttempts = 3, indexRetryBackoffSeconds = 10)

    private val service: JobIndexSyncServiceImpl by lazy {
        JobIndexSyncServiceImpl(documentBuilder, indexManager, failureRepository, properties)
    }

    @Test
    fun `Document가 있으면 색인을 갱신하고 기존 실패 기록을 지운다`() {
        val document = documentOf()
        given(documentBuilder.buildFor(1L)).willReturn(document)

        service.syncOne(1L)

        verify(indexManager).upsert(document)
        verify(indexManager, never()).delete(anyLong())
        verify(failureRepository).deleteByJobId(1L)
    }

    @Test
    fun `Document가 없으면(비공개 대상) 색인에서 제거한다`() {
        given(documentBuilder.buildFor(1L)).willReturn(null)

        service.syncOne(1L)

        verify(indexManager).delete(1L)
        verify(failureRepository).deleteByJobId(1L)
    }

    @Test
    fun `색인 실패는 새 실패 기록을 만들고 Backoff로 다음 재시도 시각을 계산한다`() {
        given(documentBuilder.buildFor(1L)).willReturn(documentOf())
        given(failureRepository.findByJobId(1L)).willReturn(null)
        willThrow(RuntimeException("Connection refused")).given(indexManager).upsert(anyDocument())

        val before = LocalDateTime.now()
        service.syncOne(1L)

        verify(failureRepository).save(failureCaptor.capture() ?: newFailure())
        val saved = failureCaptor.value
        assertThat(saved.attemptCount).isEqualTo(1)
        assertThat(saved.operation).isEqualTo(SearchIndexOperation.UPSERT)
        assertThat(saved.nextRetryAt).isAfter(before.plusSeconds(9))
        assertThat(saved.lastError).contains("Connection refused")
    }

    @Test
    fun `최대 재시도 횟수를 넘기면 다음 재시도 시각 없이 영구 실패로 남는다`() {
        given(documentBuilder.buildFor(1L)).willReturn(documentOf())
        val existing = newFailure().apply { attemptCount = 2 }
        given(failureRepository.findByJobId(1L)).willReturn(existing)
        willThrow(RuntimeException("boom")).given(indexManager).upsert(anyDocument())

        service.syncOne(1L)

        verify(failureRepository).save(failureCaptor.capture() ?: newFailure())
        val saved = failureCaptor.value
        assertThat(saved.attemptCount).isEqualTo(3)
        assertThat(saved.nextRetryAt).isNull()
    }

    @Test
    fun `Elasticsearch 예외 메시지에 민감정보가 포함돼도 원문 그대로 저장하지 않고 길이만 제한한다`() {
        given(documentBuilder.buildFor(1L)).willReturn(documentOf())
        given(failureRepository.findByJobId(1L)).willReturn(null)
        val longMessage = "x".repeat(2000)
        willThrow(RuntimeException(longMessage)).given(indexManager).upsert(anyDocument())

        service.syncOne(1L)

        verify(failureRepository).save(failureCaptor.capture() ?: newFailure())
        assertThat(failureCaptor.value.lastError).hasSize(1000)
    }

    // Kotlin non-null 파라미터에 bare any()를 쓰면 null이 반환되어 NPE가 나므로 Elvis로 기본값을
    // 준다(JobServiceTest.anyJob과 같은 이유).
    private fun anyDocument(): JobSearchDocument =
        org.mockito.ArgumentMatchers.any(JobSearchDocument::class.java) ?: documentOf()

    private fun newFailure() = SearchIndexFailure(1L, SearchIndexOperation.UPSERT)

    private fun documentOf() =
        JobSearchDocument(
            id = "1",
            jobId = 1L,
            title = "2026 상반기 백엔드 채용",
            content = null,
            postingType = "MOU",
            applicationMethod = "EXTERNAL",
            status = "PUBLISHED",
            companyId = 1L,
            companyName = "인력개발원",
            companyType = "GENERAL",
            companyLogoFileId = null,
            sourceName = null,
            targetGrade = null,
            capacity = null,
            firstComeServed = false,
            viewCount = 0,
            publishedAt = LocalDateTime.now(),
            startDate = null,
            endDate = null,
        )
}
