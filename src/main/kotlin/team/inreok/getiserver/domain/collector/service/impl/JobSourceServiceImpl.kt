package team.inreok.getiserver.domain.collector.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.collector.dto.JobSourceListResponse
import team.inreok.getiserver.domain.collector.dto.JobSourceResponse
import team.inreok.getiserver.domain.collector.dto.JobSourceUpdateRequest
import team.inreok.getiserver.domain.collector.dto.JobSourceUpdateResponse
import team.inreok.getiserver.domain.collector.dto.PublicJobSourceListResponse
import team.inreok.getiserver.domain.collector.dto.PublicJobSourceResponse
import team.inreok.getiserver.domain.collector.entity.type.CollectionRunStatus
import team.inreok.getiserver.domain.collector.entity.type.JobSourceApprovalStatus
import team.inreok.getiserver.domain.collector.exception.CollectorAlreadyRunningException
import team.inreok.getiserver.domain.collector.exception.JobSourceNotFoundException
import team.inreok.getiserver.domain.collector.exception.SourceNotApprovedException
import team.inreok.getiserver.domain.collector.exception.SourceNotConfiguredException
import team.inreok.getiserver.domain.collector.provider.ProviderRegistry
import team.inreok.getiserver.domain.collector.repository.CollectionRunRepository
import team.inreok.getiserver.domain.collector.repository.JobSourceRepository
import team.inreok.getiserver.domain.collector.service.JobSourceService

@Service
class JobSourceServiceImpl(
    private val jobSourceRepository: JobSourceRepository,
    private val collectionRunRepository: CollectionRunRepository,
    private val providerRegistry: ProviderRegistry,
) : JobSourceService {
    @Transactional(readOnly = true)
    override fun list(): JobSourceListResponse =
        JobSourceListResponse(
            sources =
                jobSourceRepository.findAllByOrderBySourceCodeAsc().map {
                    JobSourceResponse.from(
                        it,
                        providerRegistry,
                    )
                },
        )

    @Transactional(readOnly = true)
    override fun listPublic(activeOnly: Boolean): PublicJobSourceListResponse {
        val sources = jobSourceRepository.findAllByOrderBySourceCodeAsc().filter { !activeOnly || it.enabled }
        return PublicJobSourceListResponse(sources = sources.map(PublicJobSourceResponse::from))
    }

    // 활성화 조건(승인·설정·중복 실행) 각각이 서로 다른 409 Error Code에 대응해야 해서 조건마다
    // 별도 예외를 던진다. 하나의 예외로 합치면 Client가 원인을 구분할 수 없다.
    @Suppress("ThrowsCount")
    @Transactional
    override fun updateEnabled(
        sourceId: Long,
        request: JobSourceUpdateRequest,
    ): JobSourceUpdateResponse {
        val source = jobSourceRepository.findById(sourceId).orElseThrow { JobSourceNotFoundException(sourceId) }
        val enabled = requireNotNull(request.enabled)

        if (enabled) {
            if (source.approvalStatus != JobSourceApprovalStatus.READY) throw SourceNotApprovedException(sourceId)
            if (!providerRegistry.isConfigured(source.sourceCode)) throw SourceNotConfiguredException(sourceId)
            if (collectionRunRepository.existsBySourceIdAndStatusIn(sourceId, RUNNING_STATUSES)) {
                throw CollectorAlreadyRunningException(sourceId)
            }
        }

        source.enabled = enabled
        jobSourceRepository.flush()
        return JobSourceUpdateResponse.from(source, providerRegistry)
    }

    private companion object {
        val RUNNING_STATUSES = listOf(CollectionRunStatus.PENDING, CollectionRunStatus.RUNNING)
    }
}
