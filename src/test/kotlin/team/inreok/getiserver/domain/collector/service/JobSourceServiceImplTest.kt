package team.inreok.getiserver.domain.collector.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.collector.dto.JobSourceUpdateRequest
import team.inreok.getiserver.domain.collector.entity.JobSource
import team.inreok.getiserver.domain.collector.entity.type.CollectionRunStatus
import team.inreok.getiserver.domain.collector.entity.type.JobSourceApprovalStatus
import team.inreok.getiserver.domain.collector.entity.type.JobSourceCode
import team.inreok.getiserver.domain.collector.entity.type.JobSourceType
import team.inreok.getiserver.domain.collector.exception.CollectorAlreadyRunningException
import team.inreok.getiserver.domain.collector.exception.JobSourceNotFoundException
import team.inreok.getiserver.domain.collector.exception.SourceNotApprovedException
import team.inreok.getiserver.domain.collector.exception.SourceNotConfiguredException
import team.inreok.getiserver.domain.collector.provider.CollectorProvider
import team.inreok.getiserver.domain.collector.provider.ProviderRegistry
import team.inreok.getiserver.domain.collector.repository.CollectionRunRepository
import team.inreok.getiserver.domain.collector.repository.JobSourceRepository
import team.inreok.getiserver.domain.collector.service.impl.JobSourceServiceImpl
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class JobSourceServiceImplTest {
    @Mock
    private lateinit var jobSourceRepository: JobSourceRepository

    @Mock
    private lateinit var collectionRunRepository: CollectionRunRepository

    private fun serviceWith(vararg providers: CollectorProvider): JobSourceService =
        JobSourceServiceImpl(jobSourceRepository, collectionRunRepository, ProviderRegistry(providers.toList()))

    private fun sourceOf(
        id: Long = 1L,
        code: JobSourceCode = JobSourceCode.MMA,
        approvalStatus: JobSourceApprovalStatus = JobSourceApprovalStatus.READY,
        enabled: Boolean = false,
    ) = JobSource(
        sourceCode = code,
        name = "병역일터",
        sourceType = JobSourceType.EXTERNAL_API,
        approvalStatus = approvalStatus,
    ).apply {
        this.id = id
        this.enabled = enabled
    }

    private fun configuredProvider(code: JobSourceCode) =
        object : CollectorProvider {
            override val sourceCode = code

            override fun isConfigured() = true

            override fun collect(context: team.inreok.getiserver.domain.collector.provider.CollectorCollectionContext) =
                team.inreok.getiserver.domain.collector.provider
                    .CollectorCollectionResult(emptyList())
        }

    @Test
    fun `목록 조회는 sourceCode 오름차순으로 configured를 함께 계산해 반환한다`() {
        given(
            jobSourceRepository.findAllByOrderBySourceCodeAsc(),
        ).willReturn(listOf(sourceOf(code = JobSourceCode.MMA)))
        val service = serviceWith(configuredProvider(JobSourceCode.MMA))

        val result = service.list()

        assertThat(result.sources).hasSize(1)
        assertThat(result.sources.single().configured).isTrue()
    }

    @Test
    fun `공개 목록은 activeOnly가 true면 활성화된 수집원만 반환한다`() {
        given(jobSourceRepository.findAllByOrderBySourceCodeAsc()).willReturn(
            listOf(
                sourceOf(id = 1L, code = JobSourceCode.MMA, enabled = true),
                sourceOf(id = 2L, code = JobSourceCode.SARAMIN, enabled = false),
            ),
        )
        val service = serviceWith()

        val result = service.listPublic(activeOnly = true)

        assertThat(result.sources).hasSize(1)
        assertThat(result.sources.single().sourceCode).isEqualTo(JobSourceCode.MMA)
        assertThat(result.sources.single().active).isTrue()
    }

    @Test
    fun `존재하지 않는 수집원을 변경하려 하면 JobSourceNotFoundException이 발생한다`() {
        given(jobSourceRepository.findById(99L)).willReturn(Optional.empty())
        val service = serviceWith()

        assertThatThrownBy { service.updateEnabled(99L, JobSourceUpdateRequest(enabled = true)) }
            .isInstanceOf(JobSourceNotFoundException::class.java)
    }

    @Test
    fun `승인되지 않은 수집원은 활성화할 수 없다`() {
        val source = sourceOf(approvalStatus = JobSourceApprovalStatus.PENDING_APPROVAL)
        given(jobSourceRepository.findById(1L)).willReturn(Optional.of(source))
        val service = serviceWith(configuredProvider(JobSourceCode.MMA))

        assertThatThrownBy { service.updateEnabled(1L, JobSourceUpdateRequest(enabled = true)) }
            .isInstanceOf(SourceNotApprovedException::class.java)
    }

    @Test
    fun `Provider가 설정되지 않은 수집원은 활성화할 수 없다`() {
        val source = sourceOf(approvalStatus = JobSourceApprovalStatus.READY)
        given(jobSourceRepository.findById(1L)).willReturn(Optional.of(source))
        val service = serviceWith()

        assertThatThrownBy { service.updateEnabled(1L, JobSourceUpdateRequest(enabled = true)) }
            .isInstanceOf(SourceNotConfiguredException::class.java)
    }

    @Test
    fun `이미 실행 중인 수집원은 활성화할 수 없다`() {
        val source = sourceOf(approvalStatus = JobSourceApprovalStatus.READY)
        given(jobSourceRepository.findById(1L)).willReturn(Optional.of(source))
        given(
            collectionRunRepository.existsBySourceIdAndStatusIn(
                1L,
                listOf(CollectionRunStatus.PENDING, CollectionRunStatus.RUNNING),
            ),
        ).willReturn(true)
        val service = serviceWith(configuredProvider(JobSourceCode.MMA))

        assertThatThrownBy { service.updateEnabled(1L, JobSourceUpdateRequest(enabled = true)) }
            .isInstanceOf(CollectorAlreadyRunningException::class.java)
    }

    @Test
    fun `승인·설정·중복 실행 조건을 모두 만족하면 활성화된다`() {
        val source = sourceOf(approvalStatus = JobSourceApprovalStatus.READY)
        given(jobSourceRepository.findById(1L)).willReturn(Optional.of(source))
        given(
            collectionRunRepository.existsBySourceIdAndStatusIn(
                1L,
                listOf(CollectionRunStatus.PENDING, CollectionRunStatus.RUNNING),
            ),
        ).willReturn(false)
        val service = serviceWith(configuredProvider(JobSourceCode.MMA))

        val result = service.updateEnabled(1L, JobSourceUpdateRequest(enabled = true))

        assertThat(result.enabled).isTrue()
        assertThat(source.enabled).isTrue()
    }

    @Test
    fun `비활성화는 승인·설정 상태와 무관하게 항상 허용된다`() {
        val source = sourceOf(approvalStatus = JobSourceApprovalStatus.PENDING_APPROVAL, enabled = true)
        given(jobSourceRepository.findById(1L)).willReturn(Optional.of(source))
        val service = serviceWith()

        val result = service.updateEnabled(1L, JobSourceUpdateRequest(enabled = false))

        assertThat(result.enabled).isFalse()
    }
}
