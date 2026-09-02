package team.inreok.getiserver.domain.search.reindex.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyCollection
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anySet
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.dao.DataIntegrityViolationException
import team.inreok.getiserver.domain.ai.query.AiAnalysisSearchQueryPort
import team.inreok.getiserver.domain.ai.query.AiAnalysisSearchSnapshot
import team.inreok.getiserver.domain.job.query.JobIndexQueryPort
import team.inreok.getiserver.domain.job.query.JobIndexSnapshot
import team.inreok.getiserver.domain.search.config.SearchProperties
import team.inreok.getiserver.domain.search.document.JobSearchDocument
import team.inreok.getiserver.domain.search.entity.SearchReindexRun
import team.inreok.getiserver.domain.search.entity.type.SearchReindexStatus
import team.inreok.getiserver.domain.search.exception.ReindexAlreadyRunningException
import team.inreok.getiserver.domain.search.index.JobSearchIndexManager
import team.inreok.getiserver.domain.search.repository.SearchReindexRunRepository
import team.inreok.getiserver.domain.search.service.JobIndexDocumentBuilder
import java.time.LocalDateTime
import java.util.Optional

/**
 * `searchTaskExecutor`로 [SyncTaskExecutor]를 주입해 비동기 실행을 같은 Thread에서 즉시
 * 끝나게 만든다 — 실제 재색인 흐름(새 Index 생성 → Bulk 색인 → Alias 전환 → 실행 상태 갱신)을
 * 결정적으로 검증할 수 있다.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SearchReindexServiceImplTest {
    @Mock
    private lateinit var reindexRunRepository: SearchReindexRunRepository

    @Mock
    private lateinit var jobIndexQueryPort: JobIndexQueryPort

    @Mock
    private lateinit var documentBuilder: JobIndexDocumentBuilder

    @Mock
    private lateinit var aiAnalysisSearchQueryPort: AiAnalysisSearchQueryPort

    @Mock
    private lateinit var indexManager: JobSearchIndexManager

    private val properties = SearchProperties(reindexBatchSize = 2)

    private val service: SearchReindexServiceImpl by lazy {
        SearchReindexServiceImpl(
            reindexRunRepository,
            jobIndexQueryPort,
            documentBuilder,
            aiAnalysisSearchQueryPort,
            indexManager,
            SyncTaskExecutor(),
            properties,
        )
    }

    @Test
    fun `이미 진행 중인 재색인이 있으면 REINDEX_ALREADY_RUNNING으로 거부한다`() {
        given(reindexRunRepository.existsByStatusIn(anyStatuses())).willReturn(true)

        assertThatThrownBy { service.triggerReindex() }
            .isInstanceOf(ReindexAlreadyRunningException::class.java)
        verify(reindexRunRepository, never()).saveAndFlush(anyRun())
    }

    @Test
    fun `existsByStatusIn 확인과 저장 사이의 동시 요청은 DB 제약 위반을 REINDEX_ALREADY_RUNNING으로 변환한다`() {
        // existsByStatusIn은 통과했지만(TOCTOU), uk_search_reindex_runs_active_singleton
        // Partial Unique Index(V10 Migration)가 실제로는 막는 상황을 재현한다(PR #70 Review 반영).
        given(reindexRunRepository.existsByStatusIn(anyStatuses())).willReturn(false)
        willThrow(DataIntegrityViolationException("duplicate key value violates unique constraint"))
            .given(reindexRunRepository)
            .saveAndFlush(anyRun())

        assertThatThrownBy { service.triggerReindex() }
            .isInstanceOf(ReindexAlreadyRunningException::class.java)
    }

    @Test
    fun `모든 공고를 성공적으로 색인하면 SUCCESS로 완료하고 Alias를 전환한다`() {
        given(reindexRunRepository.existsByStatusIn(anyStatuses())).willReturn(false)
        given(reindexRunRepository.saveAndFlush(anyRun())).willAnswer {
            (it.arguments[0] as SearchReindexRun).apply {
                id =
                    1L
            }
        }
        given(reindexRunRepository.findById(1L)).willAnswer { Optional.of(runOf(1L)) }
        given(indexManager.createNewPhysicalIndex()).willReturn("jobs-search-20260804")
        given(indexManager.resolveIndicesBehindAlias()).willReturn(setOf("jobs-search-old"))

        given(jobIndexQueryPort.findForReindex(0L, 2)).willReturn(listOf(snapshotOf(1L), snapshotOf(2L)))
        given(jobIndexQueryPort.findForReindex(2L, 2)).willReturn(emptyList())
        given(documentBuilder.build(anySnapshot(), anyAiSnapshot())).willReturn(documentOf(1L))

        val run = service.triggerReindex()

        assertThat(run.id).isEqualTo(1L)
        verify(indexManager).bulkIndex(eqString("jobs-search-20260804"), anyDocumentList())
        verify(indexManager).switchAlias(eqString("jobs-search-20260804"), eqSet(setOf("jobs-search-old")))
        verify(indexManager).deleteIndex("jobs-search-old")
        verify(indexManager, never()).deleteIndex("jobs-search-20260804")
    }

    @Test
    fun `Document 생성 중 일부가 실패해도 나머지는 색인하고 PARTIAL_SUCCESS로 남긴다`() {
        given(reindexRunRepository.existsByStatusIn(anyStatuses())).willReturn(false)
        given(reindexRunRepository.saveAndFlush(anyRun())).willAnswer {
            (it.arguments[0] as SearchReindexRun).apply {
                id =
                    1L
            }
        }
        given(reindexRunRepository.findById(1L)).willAnswer { Optional.of(runOf(1L)) }
        given(indexManager.createNewPhysicalIndex()).willReturn("jobs-search-1")
        given(indexManager.resolveIndicesBehindAlias()).willReturn(emptySet())

        given(jobIndexQueryPort.findForReindex(0L, 2)).willReturn(listOf(snapshotOf(1L), snapshotOf(2L)))
        given(jobIndexQueryPort.findForReindex(2L, 2)).willReturn(emptyList())
        given(documentBuilder.build(snapshotOf(1L), null)).willReturn(documentOf(1L))
        willThrow(RuntimeException("bad data")).given(documentBuilder).build(snapshotOf(2L), null)

        service.triggerReindex()

        verify(indexManager).switchAlias(anyString(), anySet() ?: emptySet())
    }

    @Test
    fun `AI 분석 결과를 Page 단위로 한 번만 Batch 조회해서 N+1 없이 각 Document에 반영한다`() {
        given(reindexRunRepository.existsByStatusIn(anyStatuses())).willReturn(false)
        given(reindexRunRepository.saveAndFlush(anyRun())).willAnswer {
            (it.arguments[0] as SearchReindexRun).apply {
                id =
                    1L
            }
        }
        given(reindexRunRepository.findById(1L)).willAnswer { Optional.of(runOf(1L)) }
        given(indexManager.createNewPhysicalIndex()).willReturn("jobs-search-1")
        given(indexManager.resolveIndicesBehindAlias()).willReturn(emptySet())

        // 같은 Snapshot 객체를 Stub 설정과 검증 양쪽에서 재사용한다 -- snapshotOf가 내부적으로
        // LocalDateTime.now()를 담으므로, 매번 새로 호출하면 Data Class equals()가 나노초
        // 단위 차이로 서로 다른 값을 만들어 Argument 매칭이 깨질 수 있다.
        val snapshot1 = snapshotOf(1L)
        val snapshot2 = snapshotOf(2L)
        given(jobIndexQueryPort.findForReindex(0L, 2)).willReturn(listOf(snapshot1, snapshot2))
        given(jobIndexQueryPort.findForReindex(2L, 2)).willReturn(emptyList())
        val aiSnapshot =
            AiAnalysisSearchSnapshot(
                requiredTechStackIds = listOf(10L),
                preferredTechStackIds = emptyList(),
                highSchoolGraduateFit = "SUITABLE",
                entryLevelFit = "SUITABLE",
                difficulty = "NORMAL",
            )
        given(aiAnalysisSearchQueryPort.findCompletedByJobIds(listOf(1L, 2L)))
            .willReturn(mapOf(1L to aiSnapshot))
        given(documentBuilder.build(snapshot1, aiSnapshot)).willReturn(documentOf(1L))
        given(documentBuilder.build(snapshot2, null)).willReturn(documentOf(2L))

        service.triggerReindex()

        // Page(공고 2건)당 정확히 한 번만 호출된다 -- 공고 건수만큼 반복 조회하면 N+1이 된다.
        verify(aiAnalysisSearchQueryPort, org.mockito.Mockito.times(1)).findCompletedByJobIds(listOf(1L, 2L))
        verify(documentBuilder).build(snapshot1, aiSnapshot)
        verify(documentBuilder).build(snapshot2, null)
    }

    @Test
    fun `재색인 도중 예외가 나면 새로 만든 Index만 지우고 기존 Alias는 건드리지 않는다`() {
        given(reindexRunRepository.existsByStatusIn(anyStatuses())).willReturn(false)
        given(reindexRunRepository.saveAndFlush(anyRun())).willAnswer {
            (it.arguments[0] as SearchReindexRun).apply {
                id =
                    1L
            }
        }
        given(reindexRunRepository.findById(1L)).willAnswer { Optional.of(runOf(1L)) }
        given(indexManager.createNewPhysicalIndex()).willReturn("jobs-search-1")
        given(jobIndexQueryPort.findForReindex(anyLong(), anyInt()))
            .willThrow(RuntimeException("Postgres down"))

        service.triggerReindex()

        verify(indexManager).deleteIndex("jobs-search-1")
        verify(indexManager, never()).switchAlias(anyString(), anySet() ?: emptySet())
    }

    // Kotlin non-null 파라미터에 bare any()를 쓰면 null이 반환되어 NPE가 나므로 Elvis/전용 Matcher로
    // 기본값을 준다(JobServiceTest.anyJob과 같은 이유).
    private fun anyStatuses(): Collection<SearchReindexStatus> = anyCollection() ?: emptyList()

    private fun anyRun(): SearchReindexRun =
        org.mockito.ArgumentMatchers.any(SearchReindexRun::class.java) ?: SearchReindexRun()

    private fun anySnapshot(): JobIndexSnapshot =
        org.mockito.ArgumentMatchers.any(JobIndexSnapshot::class.java) ?: snapshotOf(0L)

    // AiAnalysisSearchSnapshot?는 Kotlin에서 Nullable 파라미터이므로(null도 유효한 값), Class를
    // 지정하지 않은 any()로 null을 포함해 무엇이든 매칭한다.
    private fun anyAiSnapshot(): AiAnalysisSearchSnapshot? = org.mockito.ArgumentMatchers.any()

    private fun anyString(): String = org.mockito.ArgumentMatchers.anyString() ?: ""

    private fun eqString(value: String): String = org.mockito.ArgumentMatchers.eq(value) ?: value

    private fun eqSet(value: Set<String>): Set<String> = org.mockito.ArgumentMatchers.eq(value) ?: value

    private fun anyDocumentList(): List<JobSearchDocument> = org.mockito.ArgumentMatchers.anyList() ?: emptyList()

    private fun runOf(id: Long) = SearchReindexRun().apply { this.id = id }

    private fun snapshotOf(jobId: Long) =
        JobIndexSnapshot(
            jobId = jobId,
            title = "공고 $jobId",
            content = null,
            postingType = "MOU",
            applicationMethod = "EXTERNAL",
            status = "PUBLISHED",
            companyId = 1L,
            targetGrade = null,
            capacity = null,
            firstComeServed = false,
            viewCount = 0,
            publishedAt = LocalDateTime.now(),
            startDate = null,
            endDate = null,
            sourceName = null,
        )

    private fun documentOf(jobId: Long) =
        JobSearchDocument(
            id = jobId.toString(),
            jobId = jobId,
            title = "공고 $jobId",
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
