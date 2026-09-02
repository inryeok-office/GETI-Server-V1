package team.inreok.getiserver.domain.collector.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.atLeast
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.core.task.TaskExecutor
import team.inreok.getiserver.domain.collector.entity.CollectionRun
import team.inreok.getiserver.domain.collector.entity.CollectionRunError
import team.inreok.getiserver.domain.collector.entity.JobSource
import team.inreok.getiserver.domain.collector.entity.type.CollectionRunStatus
import team.inreok.getiserver.domain.collector.entity.type.CollectorAction
import team.inreok.getiserver.domain.collector.entity.type.JobDataQualityStatus
import team.inreok.getiserver.domain.collector.entity.type.JobSourceApprovalStatus
import team.inreok.getiserver.domain.collector.entity.type.JobSourceCode
import team.inreok.getiserver.domain.collector.entity.type.JobSourceType
import team.inreok.getiserver.domain.collector.exception.CollectorAlreadyRunningException
import team.inreok.getiserver.domain.collector.exception.JobSourceNotFoundException
import team.inreok.getiserver.domain.collector.exception.SourceNotApprovedException
import team.inreok.getiserver.domain.collector.exception.SourceNotConfiguredException
import team.inreok.getiserver.domain.collector.notification.service.CollectionRunNotificationSender
import team.inreok.getiserver.domain.collector.notification.service.JobNotificationService
import team.inreok.getiserver.domain.collector.provider.CollectorCollectionContext
import team.inreok.getiserver.domain.collector.provider.CollectorCollectionResult
import team.inreok.getiserver.domain.collector.provider.CollectorProvider
import team.inreok.getiserver.domain.collector.provider.CollectorProviderException
import team.inreok.getiserver.domain.collector.provider.NormalizedCollectedJob
import team.inreok.getiserver.domain.collector.provider.ProviderRegistry
import team.inreok.getiserver.domain.collector.repository.CollectionRunErrorRepository
import team.inreok.getiserver.domain.collector.repository.CollectionRunRepository
import team.inreok.getiserver.domain.collector.repository.JobSourceRepository
import team.inreok.getiserver.domain.collector.service.impl.CollectorExecutionServiceImpl
import team.inreok.getiserver.domain.company.external.CompanyExternalImportCommand
import team.inreok.getiserver.domain.company.external.CompanyExternalImportResult
import team.inreok.getiserver.domain.company.external.CompanyExternalImportUseCase
import team.inreok.getiserver.domain.job.upsert.CollectedJobUpsertCommand
import team.inreok.getiserver.domain.job.upsert.CollectedJobUpsertResult
import team.inreok.getiserver.domain.job.upsert.CollectedJobUpsertUseCase
import team.inreok.getiserver.domain.job.upsert.JobImportOutcome
import java.time.LocalDateTime
import java.util.Optional

/**
 * 실제 Provider·Job/Company Upsert가 없는 이번 범위에서 Orchestrator의 격리·집계·비동기 접수
 * 규칙만 검증하는 Test다. [FakeCollectorProvider]는 이 Test 전용이며 Production Source Set에는
 * 존재하지 않는다(Issue #62 요구사항).
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

    @Mock
    private lateinit var companyExternalImportUseCase: CompanyExternalImportUseCase

    @Mock
    private lateinit var jobNotificationService: JobNotificationService

    @Mock
    private lateinit var collectionRunNotificationSender: CollectionRunNotificationSender

    // 실제 Bean은 별도 Thread에서 실행하지만, Test는 결정적인 결과 확인을 위해 호출 Thread에서
    // 즉시 실행하는 동기 Fake를 사용한다.
    private val synchronousExecutor = TaskExecutor { command -> command.run() }

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
        workRegion: String? = null,
        employmentType: String? = null,
    ) = NormalizedCollectedJob(
        sourceCode = JobSourceCode.MMA,
        externalJobId = externalJobId,
        title = "백엔드 개발자",
        companyName = companyName,
        collectedAt = now,
        dataQualityStatus = quality,
        missingFields = if (quality == JobDataQualityStatus.PARTIAL) listOf("content") else emptyList(),
        workRegion = workRegion,
        employmentType = employmentType,
    )

    // Kotlin non-null 파라미터에 bare any()를 쓰면 null 반환으로 NPE가 나므로 Elvis로 기본값을
    // 채운다(CompanyServiceTest.anyCompany()와 동일한 방식).
    private fun anyCollectionRun(): CollectionRun =
        any(CollectionRun::class.java) ?: CollectionRun(sourceId = 0, action = CollectorAction.SYNC, startedAt = now)

    private fun anyCollectionRunError(): CollectionRunError =
        any(CollectionRunError::class.java)
            ?: CollectionRunError(runId = 0, code = "x", message = "x", occurredAt = now)

    // ArgumentCaptor.capture()의 Elvis 대체값은 Matcher가 아닌 평범한 객체여야 한다 --
    // anyUpsertCommand()를 쓰면 Matcher가 두 번 등록되어 InvalidUseOfMatchersException이 난다.
    private fun fallbackUpsertCommand(): CollectedJobUpsertCommand =
        CollectedJobUpsertCommand(
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

    private fun anyImportCommand(): CompanyExternalImportCommand =
        any(CompanyExternalImportCommand::class.java) ?: CompanyExternalImportCommand("x", "x")

    private val fallbackTrigger =
        team.inreok.getiserver.domain.collector.notification.service.JobNotificationTrigger(
            jobId = 0,
            sourceCode = "x",
            sourceDisplayName = "x",
            title = "x",
            companyName = "x",
            externalUrl = null,
            recruitmentEndedAt = null,
        )

    private fun anyTrigger(): team.inreok.getiserver.domain.collector.notification.service.JobNotificationTrigger =
        any(team.inreok.getiserver.domain.collector.notification.service.JobNotificationTrigger::class.java)
            ?: fallbackTrigger

    // 실제 JPA saveAndFlush는 Insert 시 자동 생성 ID를 채워 돌려주지만 Mock은 그렇지 않다.
    // requireNotNull(run.id)를 쓰는 Production 코드가 정상 동작하도록 최초 저장에서 ID를 채워
    // 넣는다(CompanyServiceTest.saveAndFlush Stub와 같은 목적).
    private var nextRunId = 1L

    private fun assignIdIfAbsent(invocation: org.mockito.invocation.InvocationOnMock): CollectionRun =
        (invocation.arguments[0] as CollectionRun).apply { if (id == null) id = nextRunId++ }

    private fun serviceWith(provider: CollectorProvider?): CollectorExecutionService {
        val registry = ProviderRegistry(listOfNotNull(provider))
        return CollectorExecutionServiceImpl(
            jobSourceRepository,
            collectionRunRepository,
            collectionRunErrorRepository,
            registry,
            collectedJobUpsertUseCase,
            companyExternalImportUseCase,
            jobNotificationService,
            collectionRunNotificationSender,
            synchronousExecutor,
        )
    }

    private fun givenCompanyResolves(companyId: Long = 100L) {
        given(companyExternalImportUseCase.findOrCreateExternal(anyImportCommand()))
            .willReturn(CompanyExternalImportResult(companyId = companyId, created = false))
    }

    // --- runDailyCollection ---

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
        givenCompanyResolves()
        given(collectedJobUpsertUseCase.upsert(anyUpsertCommand()))
            .willReturn(CollectedJobUpsertResult(jobId = 1L, outcome = JobImportOutcome.CREATED, published = true))

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
        assertThat(finalRun.action).isEqualTo(CollectorAction.SYNC)
        assertThat(finalRun.status).isEqualTo(CollectionRunStatus.SUCCESS)
        assertThat(finalRun.createdCount).isEqualTo(2)
        assertThat(finalRun.updatedCount).isZero()
        assertThat(finalRun.successCount).isEqualTo(2)
        assertThat(finalRun.failureCount).isEqualTo(0)
        assertThat(finalRun.partialQualityCount).isEqualTo(1)
    }

    @Test
    fun `생성 갱신 변경없음 실패를 분리 집계하고 기존 성공 실패 불변식을 유지한다`() {
        val source = sourceOf()
        given(jobSourceRepository.findAllByOrderBySourceCodeAsc()).willReturn(listOf(source))
        given(collectionRunRepository.existsBySourceIdAndStatusIn(1L, ACTIVE_STATUSES)).willReturn(false)
        given(collectionRunRepository.saveAndFlush(anyCollectionRun())).willAnswer(::assignIdIfAbsent)
        givenCompanyResolves()
        given(collectedJobUpsertUseCase.upsert(anyUpsertCommand()))
            .willReturn(CollectedJobUpsertResult(jobId = 1L, outcome = JobImportOutcome.CREATED, published = true))
            .willReturn(CollectedJobUpsertResult(jobId = 2L, outcome = JobImportOutcome.UPDATED, published = true))
            .willReturn(CollectedJobUpsertResult(jobId = 3L, outcome = JobImportOutcome.UNCHANGED, published = true))
            .willThrow(IllegalStateException("upsert 실패"))
        val provider =
            FakeCollectorProvider(JobSourceCode.MMA) {
                CollectorCollectionResult(
                    jobs = listOf(jobOf("CREATED"), jobOf("UPDATED"), jobOf("UNCHANGED"), jobOf("FAILED")),
                )
            }

        serviceWith(provider).runDailyCollection()

        val captor = ArgumentCaptor.forClass(CollectionRun::class.java)
        verify(collectionRunRepository, times(3)).saveAndFlush(captor.capture())
        val finalRun = captor.allValues.last()
        assertThat(finalRun.createdCount).isEqualTo(1)
        assertThat(finalRun.updatedCount).isEqualTo(1)
        assertThat(finalRun.successCount).isEqualTo(3)
        assertThat(finalRun.failureCount).isEqualTo(1)
        assertThat(finalRun.totalCount).isEqualTo(finalRun.successCount + finalRun.failureCount)
        assertThat(finalRun.status).isEqualTo(CollectionRunStatus.PARTIAL_SUCCESS)
        verify(collectionRunErrorRepository).save(anyCollectionRunError())
    }

    // Provider가 준 근무지·고용형태를 Job에 넘기지 않으면 외부 수집 공고의 공고 카드가 영원히
    // 비어 있게 된다(Issue #169). 이전에는 Discord 알림과 적합성 판정에만 썼다.
    @Test
    fun `Provider가 준 근무지와 고용형태를 Upsert Command에 담아 전달한다`() {
        val source = sourceOf()
        given(jobSourceRepository.findAllByOrderBySourceCodeAsc()).willReturn(listOf(source))
        given(collectionRunRepository.existsBySourceIdAndStatusIn(1L, ACTIVE_STATUSES)).willReturn(false)
        given(collectionRunRepository.saveAndFlush(anyCollectionRun()))
            .willAnswer(::assignIdIfAbsent)
        givenCompanyResolves()
        given(collectedJobUpsertUseCase.upsert(anyUpsertCommand()))
            .willReturn(CollectedJobUpsertResult(jobId = 1L, outcome = JobImportOutcome.CREATED, published = true))

        val provider =
            FakeCollectorProvider(JobSourceCode.MMA) {
                CollectorCollectionResult(
                    jobs =
                        listOf(
                            jobOf("EXT-1", workRegion = "경상남도 창원시 의창구", employmentType = "주5일"),
                        ),
                )
            }

        serviceWith(provider).runDailyCollection()

        val captor = ArgumentCaptor.forClass(CollectedJobUpsertCommand::class.java)
        verify(collectedJobUpsertUseCase).upsert(captor.capture() ?: fallbackUpsertCommand())
        assertThat(captor.value.location).isEqualTo("경상남도 창원시 의창구")
        assertThat(captor.value.employmentType).isEqualTo("주5일")
    }

    @Test
    fun `적합성 판정에서 제외된 공고는 저장·알림 시도 없이 성공도 실패도 아닌 것으로 집계된다`() {
        // GETI는 최대한 많은 공고를 저장하는 서비스가 아니라 마이스터고 학생이 실제로 지원·진로
        // 탐색에 활용할 수 있는 공고만 수집한다(Issue #62 확장 범위). 대졸 필수 공고는
        // JobEligibilityPolicy가 EXCLUDED로 판정해야 한다.
        val source = sourceOf()
        given(jobSourceRepository.findAllByOrderBySourceCodeAsc()).willReturn(listOf(source))
        given(collectionRunRepository.existsBySourceIdAndStatusIn(1L, ACTIVE_STATUSES)).willReturn(false)
        given(collectionRunRepository.saveAndFlush(anyCollectionRun())).willAnswer(::assignIdIfAbsent)

        val excludedJob =
            NormalizedCollectedJob(
                sourceCode = JobSourceCode.MMA,
                externalJobId = "EXT-EXCLUDED",
                title = "백엔드 개발자",
                companyName = "인력개발원",
                collectedAt = now,
                dataQualityStatus = JobDataQualityStatus.COMPLETE,
                qualificationDetail = "4년제 대학교 졸업 필수",
            )
        val provider =
            FakeCollectorProvider(JobSourceCode.MMA) { CollectorCollectionResult(jobs = listOf(excludedJob)) }
        val service = serviceWith(provider)

        service.runDailyCollection()

        val captor = ArgumentCaptor.forClass(CollectionRun::class.java)
        verify(collectionRunRepository, times(3)).saveAndFlush(captor.capture())
        val finalRun = captor.allValues.last()
        assertThat(finalRun.status).isEqualTo(CollectionRunStatus.SUCCESS)
        assertThat(finalRun.successCount).isEqualTo(0)
        assertThat(finalRun.failureCount).isEqualTo(0)
        verify(companyExternalImportUseCase, never()).findOrCreateExternal(anyImportCommand())
        verify(collectedJobUpsertUseCase, never()).upsert(anyUpsertCommand())
    }

    @Test
    fun `적합성 판정에서 제외된 공고의 품질 경고는 집계하지 않는다`() {
        // partialQualityCount는 실제로 저장된 공고 기준으로 집계해야 한다. 제외된 공고까지
        // 포함하면 Discord 요약 알림의 "전체 공고 수"보다 "품질 경고" 건수가 더 커지는 등
        // 두 집계 기준이 어긋난다(실제 API로 재현해 발견, Issue #62 확장 범위).
        val source = sourceOf()
        given(jobSourceRepository.findAllByOrderBySourceCodeAsc()).willReturn(listOf(source))
        given(collectionRunRepository.existsBySourceIdAndStatusIn(1L, ACTIVE_STATUSES)).willReturn(false)
        given(collectionRunRepository.saveAndFlush(anyCollectionRun())).willAnswer(::assignIdIfAbsent)

        val excludedPartialJob =
            NormalizedCollectedJob(
                sourceCode = JobSourceCode.MMA,
                externalJobId = "EXT-EXCLUDED-PARTIAL",
                title = "백엔드 개발자",
                companyName = "인력개발원",
                collectedAt = now,
                dataQualityStatus = JobDataQualityStatus.PARTIAL,
                qualificationDetail = "4년제 대학교 졸업 필수",
            )
        val provider =
            FakeCollectorProvider(JobSourceCode.MMA) { CollectorCollectionResult(jobs = listOf(excludedPartialJob)) }
        val service = serviceWith(provider)

        service.runDailyCollection()

        val captor = ArgumentCaptor.forClass(CollectionRun::class.java)
        verify(collectionRunRepository, times(3)).saveAndFlush(captor.capture())
        val finalRun = captor.allValues.last()
        assertThat(finalRun.partialQualityCount).isEqualTo(0)
    }

    @Test
    fun `Job 반영이 실패하면 해당 공고만 실패로 기록되고 다른 공고 처리는 계속된다`() {
        val source = sourceOf()
        given(jobSourceRepository.findAllByOrderBySourceCodeAsc()).willReturn(listOf(source))
        given(collectionRunRepository.existsBySourceIdAndStatusIn(1L, ACTIVE_STATUSES)).willReturn(false)
        given(collectionRunRepository.saveAndFlush(anyCollectionRun()))
            .willAnswer(::assignIdIfAbsent)
        givenCompanyResolves()
        given(collectedJobUpsertUseCase.upsert(anyUpsertCommand()))
            .willThrow(IllegalStateException("upsert 실패"))

        val provider =
            FakeCollectorProvider(JobSourceCode.MMA) {
                CollectorCollectionResult(jobs = listOf(jobOf("EXT-1"), jobOf("EXT-2")))
            }
        val service = serviceWith(provider)

        service.runDailyCollection()

        verify(collectionRunErrorRepository, times(2)).save(anyCollectionRunError())
        val captor = ArgumentCaptor.forClass(CollectionRun::class.java)
        verify(collectionRunRepository, times(3)).saveAndFlush(captor.capture())
        val finalRun = captor.allValues.last()
        assertThat(finalRun.status).isEqualTo(CollectionRunStatus.FAILED)
        assertThat(finalRun.createdCount).isZero()
        assertThat(finalRun.updatedCount).isZero()
        assertThat(finalRun.successCount).isZero()
        assertThat(finalRun.failureCount).isEqualTo(2)
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
        givenCompanyResolves()
        given(collectedJobUpsertUseCase.upsert(anyUpsertCommand()))
            .willReturn(CollectedJobUpsertResult(jobId = 1L, outcome = JobImportOutcome.CREATED, published = true))

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
                companyExternalImportUseCase,
                jobNotificationService,
                collectionRunNotificationSender,
                synchronousExecutor,
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

    @Test
    fun `공고가 새로 생성되면 Discord 알림 등록을 시도한다`() {
        val source = sourceOf()
        given(jobSourceRepository.findAllByOrderBySourceCodeAsc()).willReturn(listOf(source))
        given(collectionRunRepository.existsBySourceIdAndStatusIn(1L, ACTIVE_STATUSES)).willReturn(false)
        given(collectionRunRepository.saveAndFlush(anyCollectionRun())).willAnswer(::assignIdIfAbsent)
        givenCompanyResolves()
        given(collectedJobUpsertUseCase.upsert(anyUpsertCommand()))
            .willReturn(CollectedJobUpsertResult(jobId = 42L, outcome = JobImportOutcome.CREATED, published = true))
        val provider =
            FakeCollectorProvider(JobSourceCode.MMA) { CollectorCollectionResult(jobs = listOf(jobOf("EXT-1"))) }
        val service = serviceWith(provider)

        service.runDailyCollection()

        // ArgumentCaptor.capture()를 Kotlin non-null 파라미터 2개짜리 호출에 eq()와 함께 쓰면
        // Elvis 평가 순서 때문에 Matcher가 중복 등록된다(InvalidUseOfMatchersException) — 대신
        // argThat으로 값 하나만 뽑아 확인한다.
        var capturedJobId: Long? = null
        verify(jobNotificationService).enqueueIfEligible(
            org.mockito.ArgumentMatchers.argThat { trigger ->
                capturedJobId = trigger?.jobId
                true
            }
                ?: fallbackTrigger,
            org.mockito.ArgumentMatchers.eq(true),
        )
        assertThat(capturedJobId).isEqualTo(42L)
    }

    @Test
    fun `공고가 변경 없음(UNCHANGED)으로 반영되면 Discord 알림을 등록하지 않는다`() {
        val source = sourceOf()
        given(jobSourceRepository.findAllByOrderBySourceCodeAsc()).willReturn(listOf(source))
        given(collectionRunRepository.existsBySourceIdAndStatusIn(1L, ACTIVE_STATUSES)).willReturn(false)
        given(collectionRunRepository.saveAndFlush(anyCollectionRun())).willAnswer(::assignIdIfAbsent)
        givenCompanyResolves()
        given(collectedJobUpsertUseCase.upsert(anyUpsertCommand()))
            .willReturn(CollectedJobUpsertResult(jobId = 42L, outcome = JobImportOutcome.UNCHANGED, published = true))
        val provider =
            FakeCollectorProvider(JobSourceCode.MMA) { CollectorCollectionResult(jobs = listOf(jobOf("EXT-1"))) }
        val service = serviceWith(provider)

        service.runDailyCollection()

        verify(
            jobNotificationService,
            times(0),
        ).enqueueIfEligible(anyTrigger(), org.mockito.ArgumentMatchers.anyBoolean())
        val captor = ArgumentCaptor.forClass(CollectionRun::class.java)
        verify(collectionRunRepository, times(3)).saveAndFlush(captor.capture())
        val finalRun = captor.allValues.last()
        assertThat(finalRun.createdCount).isZero()
        assertThat(finalRun.updatedCount).isZero()
        assertThat(finalRun.successCount).isEqualTo(1)
    }

    @Test
    fun `공고가 갱신되면 updatedCount만 증가한다`() {
        val source = sourceOf()
        given(jobSourceRepository.findAllByOrderBySourceCodeAsc()).willReturn(listOf(source))
        given(collectionRunRepository.existsBySourceIdAndStatusIn(1L, ACTIVE_STATUSES)).willReturn(false)
        given(collectionRunRepository.saveAndFlush(anyCollectionRun())).willAnswer(::assignIdIfAbsent)
        givenCompanyResolves()
        given(collectedJobUpsertUseCase.upsert(anyUpsertCommand()))
            .willReturn(CollectedJobUpsertResult(jobId = 42L, outcome = JobImportOutcome.UPDATED, published = true))
        val provider =
            FakeCollectorProvider(JobSourceCode.MMA) { CollectorCollectionResult(jobs = listOf(jobOf("EXT-1"))) }

        serviceWith(provider).runDailyCollection()

        val captor = ArgumentCaptor.forClass(CollectionRun::class.java)
        verify(collectionRunRepository, times(3)).saveAndFlush(captor.capture())
        val finalRun = captor.allValues.last()
        assertThat(finalRun.createdCount).isZero()
        assertThat(finalRun.updatedCount).isEqualTo(1)
        assertThat(finalRun.successCount).isEqualTo(1)
        assertThat(finalRun.failureCount).isZero()
    }

    // --- triggerManual ---

    @Test
    fun `수동 실행은 대상 수집원의 CollectionRun을 PENDING으로 즉시 생성해 반환한다`() {
        val source = sourceOf()
        given(jobSourceRepository.findById(1L)).willReturn(Optional.of(source))
        given(collectionRunRepository.existsBySourceIdAndStatusIn(1L, ACTIVE_STATUSES)).willReturn(false)
        given(collectionRunRepository.saveAndFlush(anyCollectionRun())).willAnswer(::assignIdIfAbsent)
        givenCompanyResolves()
        given(collectedJobUpsertUseCase.upsert(anyUpsertCommand()))
            .willReturn(CollectedJobUpsertResult(jobId = 1L, outcome = JobImportOutcome.CREATED, published = true))
        val provider =
            FakeCollectorProvider(JobSourceCode.MMA) { CollectorCollectionResult(jobs = listOf(jobOf("EXT-1"))) }
        val service = serviceWith(provider)

        val runs = service.triggerManual(CollectorAction.COLLECT, listOf(1L))

        assertThat(runs).hasSize(1)
        assertThat(runs.single().action).isEqualTo(CollectorAction.COLLECT)
    }

    @Test
    fun `같은 sourceId가 중복 요청되면 CollectionRun을 한 번만 생성한다`() {
        // requireRunnable의 중복 실행 검사(existsBySourceIdAndStatusIn)는 아직 아무 Run도 저장되지
        // 않은 시점에 수행되므로, 중복 제거 없이는 [1, 1] 같은 요청이 두 검사를 모두 통과해 같은
        // Source에 CollectionRun이 2건 생성될 수 있다(PR #68 Code Review Finding #2).
        val source = sourceOf()
        given(jobSourceRepository.findById(1L)).willReturn(Optional.of(source))
        given(collectionRunRepository.existsBySourceIdAndStatusIn(1L, ACTIVE_STATUSES)).willReturn(false)
        given(collectionRunRepository.saveAndFlush(anyCollectionRun())).willAnswer(::assignIdIfAbsent)
        givenCompanyResolves()
        given(collectedJobUpsertUseCase.upsert(anyUpsertCommand()))
            .willReturn(CollectedJobUpsertResult(jobId = 1L, outcome = JobImportOutcome.CREATED, published = true))
        val provider =
            FakeCollectorProvider(JobSourceCode.MMA) { CollectorCollectionResult(jobs = listOf(jobOf("EXT-1"))) }
        val service = serviceWith(provider)

        val runs = service.triggerManual(CollectorAction.COLLECT, listOf(1L, 1L))

        assertThat(runs).hasSize(1)
    }

    @Test
    fun `존재하지 않는 수집원으로 수동 실행을 요청하면 JobSourceNotFoundException이 발생하고 실행을 만들지 않는다`() {
        given(jobSourceRepository.findById(99L)).willReturn(Optional.empty())
        val service = serviceWith(FakeCollectorProvider(JobSourceCode.MMA) { CollectorCollectionResult(emptyList()) })

        assertThatThrownBy { service.triggerManual(CollectorAction.COLLECT, listOf(99L)) }
            .isInstanceOf(JobSourceNotFoundException::class.java)
        verify(collectionRunRepository, times(0)).saveAndFlush(anyCollectionRun())
    }

    @Test
    fun `승인되지 않은 수집원으로 수동 실행을 요청하면 SourceNotApprovedException이 발생한다`() {
        val source = sourceOf(approvalStatus = JobSourceApprovalStatus.PENDING_APPROVAL)
        given(jobSourceRepository.findById(1L)).willReturn(Optional.of(source))
        val service = serviceWith(FakeCollectorProvider(JobSourceCode.MMA) { CollectorCollectionResult(emptyList()) })

        assertThatThrownBy { service.triggerManual(CollectorAction.COLLECT, listOf(1L)) }
            .isInstanceOf(SourceNotApprovedException::class.java)
    }

    @Test
    fun `설정되지 않은 수집원으로 수동 실행을 요청하면 SourceNotConfiguredException이 발생한다`() {
        val source = sourceOf()
        given(jobSourceRepository.findById(1L)).willReturn(Optional.of(source))
        // Provider가 등록되지 않아 configured=false
        val service = serviceWith(provider = null)

        assertThatThrownBy { service.triggerManual(CollectorAction.COLLECT, listOf(1L)) }
            .isInstanceOf(SourceNotConfiguredException::class.java)
    }

    @Test
    fun `이미 실행 중인 수집원으로 수동 실행을 요청하면 CollectorAlreadyRunningException이 발생한다`() {
        val source = sourceOf()
        given(jobSourceRepository.findById(1L)).willReturn(Optional.of(source))
        given(collectionRunRepository.existsBySourceIdAndStatusIn(1L, ACTIVE_STATUSES)).willReturn(true)
        val service = serviceWith(FakeCollectorProvider(JobSourceCode.MMA) { CollectorCollectionResult(emptyList()) })

        assertThatThrownBy { service.triggerManual(CollectorAction.COLLECT, listOf(1L)) }
            .isInstanceOf(CollectorAlreadyRunningException::class.java)
    }

    @Test
    fun `여러 수집원 중 하나라도 검증에 실패하면 어떤 실행도 만들지 않는다`() {
        val validSource = sourceOf(id = 1L)
        given(jobSourceRepository.findById(1L)).willReturn(Optional.of(validSource))
        given(jobSourceRepository.findById(2L)).willReturn(Optional.empty())
        val service = serviceWith(FakeCollectorProvider(JobSourceCode.MMA) { CollectorCollectionResult(emptyList()) })

        assertThatThrownBy { service.triggerManual(CollectorAction.COLLECT, listOf(1L, 2L)) }
            .isInstanceOf(JobSourceNotFoundException::class.java)
        verify(collectionRunRepository, times(0)).saveAndFlush(anyCollectionRun())
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
