package team.inreok.getiserver.domain.collector.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.atLeast
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.beans.factory.ObjectProvider
import team.inreok.getiserver.domain.collector.entity.CollectionRun
import team.inreok.getiserver.domain.collector.entity.CollectionRunError
import team.inreok.getiserver.domain.collector.entity.JobSource
import team.inreok.getiserver.domain.collector.entity.type.CollectionRunStatus
import team.inreok.getiserver.domain.collector.entity.type.JobDataQualityStatus
import team.inreok.getiserver.domain.collector.entity.type.JobSourceApprovalStatus
import team.inreok.getiserver.domain.collector.entity.type.JobSourceCode
import team.inreok.getiserver.domain.collector.entity.type.JobSourceType
import team.inreok.getiserver.domain.collector.provider.CollectorCollectionContext
import team.inreok.getiserver.domain.collector.provider.CollectorCollectionResult
import team.inreok.getiserver.domain.collector.provider.CollectorCompanyResolver
import team.inreok.getiserver.domain.collector.provider.CollectorProvider
import team.inreok.getiserver.domain.collector.provider.CollectorProviderException
import team.inreok.getiserver.domain.collector.provider.NormalizedCollectedJob
import team.inreok.getiserver.domain.collector.provider.ProviderRegistry
import team.inreok.getiserver.domain.collector.repository.CollectionRunErrorRepository
import team.inreok.getiserver.domain.collector.repository.CollectionRunRepository
import team.inreok.getiserver.domain.collector.repository.JobSourceRepository
import team.inreok.getiserver.domain.collector.service.impl.CollectorExecutionServiceImpl
import team.inreok.getiserver.domain.job.upsert.CollectedJobUpsertCommand
import team.inreok.getiserver.domain.job.upsert.CollectedJobUpsertResult
import team.inreok.getiserver.domain.job.upsert.CollectedJobUpsertUseCase
import java.time.LocalDateTime

/**
 * 실제 Provider·Job Upsert가 없는 이번 범위에서 Orchestrator의 격리·집계 규칙만 검증하는
 * Test다. [FakeCollectorProvider]와 [FakeCollectorCompanyResolver]는 이 Test 전용이며
 * Production Source Set에는 존재하지 않는다(Issue #62 요구사항).
 */
@ExtendWith(MockitoExtension::class)
class CollectorExecutionServiceImplTest {
    @Mock
    private lateinit var jobSourceRepository: JobSourceRepository

    @Mock
    private lateinit var collectionRunRepository: CollectionRunRepository

    @Mock
    private lateinit var collectionRunErrorRepository: CollectionRunErrorRepository

    @Mock
    private lateinit var collectedJobUpsertUseCase: CollectedJobUpsertUseCase

    private val now = LocalDateTime.of(2026, 8, 3, 3, 0)

    private fun sourceOf(
        id: Long = 1L,
        code: JobSourceCode = JobSourceCode.MMA,
        enabled: Boolean = true,
        approvalStatus: JobSourceApprovalStatus = JobSourceApprovalStatus.READY,
    ) = JobSource(
        sourceCode = code,
        name = "병역일터",
        sourceType = JobSourceType.EXTERNAL_API,
        approvalStatus = approvalStatus,
    ).apply {
        this.id = id
        this.enabled = enabled
    }

    private fun jobOf(
        externalJobId: String,
        quality: JobDataQualityStatus = JobDataQualityStatus.COMPLETE,
        companyName: String = "인력개발원",
    ) = NormalizedCollectedJob(
        sourceCode = JobSourceCode.MMA,
        externalJobId = externalJobId,
        title = "백엔드 개발자",
        companyName = companyName,
        collectedAt = now,
        dataQualityStatus = quality,
        missingFields = if (quality == JobDataQualityStatus.PARTIAL) listOf("content") else emptyList(),
    )

    // Kotlin non-null 파라미터에 bare any()를 쓰면 null 반환으로 NPE가 나므로 Elvis로 기본값을
    // 채운다(CompanyServiceTest.anyCompany()와 동일한 방식).
    private fun anyCollectionRun(): CollectionRun =
        any(CollectionRun::class.java) ?: CollectionRun(sourceId = 0, action = "x", startedAt = now)

    private fun anyCollectionRunError(): CollectionRunError =
        any(CollectionRunError::class.java)
            ?: CollectionRunError(runId = 0, code = "x", message = "x", occurredAt = now)

    private fun anyUpsertCommand(): CollectedJobUpsertCommand =
        any(CollectedJobUpsertCommand::class.java) ?: CollectedJobUpsertCommand(
            companyId = 0,
            sourceName = "x",
            externalJobId = "x",
            title = "x",
            content = null,
            externalUrl = null,
            startDate = null,
            endDate = null,
            publish = false,
        )

    // 실제 JPA saveAndFlush는 Insert 시 자동 생성 ID를 채워 돌려주지만 Mock은 그렇지 않다.
    // requireNotNull(run.id)를 쓰는 Production 코드가 정상 동작하도록 최초 저장에서 ID를 채워
    // 넣는다(CompanyServiceTest.saveAndFlush Stub와 같은 목적).
    private var nextRunId = 1L

    private fun assignIdIfAbsent(invocation: org.mockito.invocation.InvocationOnMock): CollectionRun =
        (invocation.arguments[0] as CollectionRun).apply { if (id == null) id = nextRunId++ }

    // ObjectProvider는 단순 조회 계약이라 Mockito Mock 대신 직접 구현한다 — Skip 경로로 끝나는
    // Test는 getIfAvailable()이 호출되지 않아 Mock Stub를 쓰면 Strict Stubbing이
    // UnnecessaryStubbingException을 던지기 때문이다.
    private fun objectProviderOf(resolver: CollectorCompanyResolver?): ObjectProvider<CollectorCompanyResolver> =
        object : ObjectProvider<CollectorCompanyResolver> {
            override fun getObject(): CollectorCompanyResolver = requireNotNull(resolver)

            override fun getIfAvailable(): CollectorCompanyResolver? = resolver
        }

    private fun serviceWith(
        provider: CollectorProvider?,
        companyResolver: CollectorCompanyResolver? = CollectorCompanyResolver { 100L },
    ): CollectorExecutionService {
        val registry = ProviderRegistry(listOfNotNull(provider))
        return CollectorExecutionServiceImpl(
            jobSourceRepository,
            collectionRunRepository,
            collectionRunErrorRepository,
            registry,
            collectedJobUpsertUseCase,
            objectProviderOf(companyResolver),
        )
    }

    @Test
    fun `등록된 Provider가 없으면 실행 이력 없이 정상 Skip한다`() {
        given(jobSourceRepository.findAllByOrderBySourceCodeAsc()).willReturn(listOf(sourceOf()))
        val service = serviceWith(provider = null)

        service.runDailyCollection()

        verify(collectionRunRepository, times(0)).saveAndFlush(anyCollectionRun())
    }

    @Test
    fun `비활성 수집원과 미승인 수집원은 실행 대상에서 제외된다`() {
        given(jobSourceRepository.findAllByOrderBySourceCodeAsc()).willReturn(
            listOf(
                sourceOf(id = 1L, enabled = false),
                sourceOf(id = 2L, approvalStatus = JobSourceApprovalStatus.PENDING_APPROVAL),
            ),
        )
        val provider = FakeCollectorProvider(JobSourceCode.MMA) { CollectorCollectionResult(emptyList()) }
        val service = serviceWith(provider)

        service.runDailyCollection()

        verify(collectionRunRepository, times(0)).saveAndFlush(anyCollectionRun())
    }

    @Test
    fun `이미 진행 중인 실행이 있으면 새 실행을 만들지 않고 Skip한다`() {
        val source = sourceOf()
        given(jobSourceRepository.findAllByOrderBySourceCodeAsc()).willReturn(listOf(source))
        given(collectionRunRepository.existsBySourceIdAndStatusIn(1L, ACTIVE_STATUSES)).willReturn(true)
        val provider = FakeCollectorProvider(JobSourceCode.MMA) { CollectorCollectionResult(emptyList()) }
        val service = serviceWith(provider)

        service.runDailyCollection()

        verify(collectionRunRepository, times(0)).saveAndFlush(anyCollectionRun())
    }

    @Test
    fun `모든 공고가 성공하면 실행은 SUCCESS이고 품질 경고 건수를 별도로 집계한다`() {
        val source = sourceOf()
        given(jobSourceRepository.findAllByOrderBySourceCodeAsc()).willReturn(listOf(source))
        given(collectionRunRepository.existsBySourceIdAndStatusIn(1L, ACTIVE_STATUSES)).willReturn(false)
        given(collectionRunRepository.saveAndFlush(anyCollectionRun()))
            .willAnswer(::assignIdIfAbsent)
        given(collectedJobUpsertUseCase.upsert(anyUpsertCommand()))
            .willReturn(CollectedJobUpsertResult(jobId = 1L, created = true, published = true))

        val provider =
            FakeCollectorProvider(JobSourceCode.MMA) {
                CollectorCollectionResult(
                    jobs =
                        listOf(
                            jobOf("EXT-1", quality = JobDataQualityStatus.COMPLETE),
                            jobOf("EXT-2", quality = JobDataQualityStatus.PARTIAL),
                        ),
                )
            }
        val service = serviceWith(provider)

        service.runDailyCollection()

        val captor = ArgumentCaptor.forClass(CollectionRun::class.java)
        verify(collectionRunRepository, times(3)).saveAndFlush(captor.capture())
        val finalRun = captor.allValues.last()
        assertThat(finalRun.status).isEqualTo(CollectionRunStatus.SUCCESS)
        assertThat(finalRun.successCount).isEqualTo(2)
        assertThat(finalRun.failureCount).isEqualTo(0)
        assertThat(finalRun.partialQualityCount).isEqualTo(1)
    }

    @Test
    fun `기업명을 해석할 수 없는 공고는 실패로 기록되고 다른 공고 처리는 계속된다`() {
        val source = sourceOf()
        given(jobSourceRepository.findAllByOrderBySourceCodeAsc()).willReturn(listOf(source))
        given(collectionRunRepository.existsBySourceIdAndStatusIn(1L, ACTIVE_STATUSES)).willReturn(false)
        given(collectionRunRepository.saveAndFlush(anyCollectionRun()))
            .willAnswer(::assignIdIfAbsent)
        // 기업명을 해석할 수 없으면 collectedJobUpsertUseCase.upsert는 아예 호출되지 않는다.

        val provider =
            FakeCollectorProvider(JobSourceCode.MMA) {
                CollectorCollectionResult(jobs = listOf(jobOf("EXT-1"), jobOf("EXT-2")))
            }
        // Company 해석 계약이 아직 없어(Blocker) resolver가 항상 null을 반환하는 상황을 재현한다.
        val service = serviceWith(provider, companyResolver = null)

        service.runDailyCollection()

        verify(collectionRunErrorRepository, times(2)).save(anyCollectionRunError())
        val captor = ArgumentCaptor.forClass(CollectionRun::class.java)
        verify(collectionRunRepository, times(3)).saveAndFlush(captor.capture())
        assertThat(captor.allValues.last().status).isEqualTo(CollectionRunStatus.FAILED)
    }

    @Test
    fun `Provider 실행 자체가 실패해도 다른 수집원은 계속 실행된다`() {
        val failingSource = sourceOf(id = 1L, code = JobSourceCode.MMA)
        val healthySource = sourceOf(id = 2L, code = JobSourceCode.SARAMIN)
        given(jobSourceRepository.findAllByOrderBySourceCodeAsc()).willReturn(listOf(failingSource, healthySource))
        given(collectionRunRepository.existsBySourceIdAndStatusIn(anyLong(), anyList()))
            .willReturn(false)
        given(collectionRunRepository.saveAndFlush(anyCollectionRun()))
            .willAnswer(::assignIdIfAbsent)
        given(collectedJobUpsertUseCase.upsert(anyUpsertCommand()))
            .willReturn(CollectedJobUpsertResult(jobId = 1L, created = true, published = true))

        val failingProvider =
            FakeCollectorProvider(JobSourceCode.MMA) { throw CollectorProviderException.Timeout() }
        val healthyProvider =
            FakeCollectorProvider(JobSourceCode.SARAMIN) { CollectorCollectionResult(jobs = listOf(jobOf("EXT-1"))) }
        val registry = ProviderRegistry(listOf(failingProvider, healthyProvider))
        val service =
            CollectorExecutionServiceImpl(
                jobSourceRepository,
                collectionRunRepository,
                collectionRunErrorRepository,
                registry,
                collectedJobUpsertUseCase,
                objectProviderOf(CollectorCompanyResolver { 100L }),
            )

        service.runDailyCollection()

        // 실패한 Source(1건: 최종 FAILED) + 성공한 Source(2건: PENDING/RUNNING 생성 + 최종 SUCCESS 저장)를
        // 합쳐 두 Source 모두 실행이 끝까지 처리되었는지 최종 상태로 확인한다.
        val captor = ArgumentCaptor.forClass(CollectionRun::class.java)
        verify(collectionRunRepository, atLeast(4)).saveAndFlush(captor.capture())
        val statusesBySource = captor.allValues.groupBy { it.sourceId }.mapValues { it.value.last().status }
        assertThat(statusesBySource[1L]).isEqualTo(CollectionRunStatus.FAILED)
        assertThat(statusesBySource[2L]).isEqualTo(CollectionRunStatus.SUCCESS)
    }

    private class FakeCollectorProvider(
        override val sourceCode: JobSourceCode,
        private val onCollect: (CollectorCollectionContext) -> CollectorCollectionResult,
    ) : CollectorProvider {
        override fun isConfigured() = true

        override fun collect(context: CollectorCollectionContext): CollectorCollectionResult = onCollect(context)
    }

    private companion object {
        val ACTIVE_STATUSES = listOf(CollectionRunStatus.PENDING, CollectionRunStatus.RUNNING)
    }
}
