package team.inreok.getiserver.domain.collector.service.impl

import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.collector.dto.CollectionRunDetailResponse
import team.inreok.getiserver.domain.collector.dto.CollectionRunErrorResponse
import team.inreok.getiserver.domain.collector.dto.CollectionRunListResponse
import team.inreok.getiserver.domain.collector.dto.CollectionRunSummaryResponse
import team.inreok.getiserver.domain.collector.entity.CollectionRun
import team.inreok.getiserver.domain.collector.entity.type.CollectionRunStatus
import team.inreok.getiserver.domain.collector.exception.CollectionRunNotFoundException
import team.inreok.getiserver.domain.collector.repository.CollectionRunErrorRepository
import team.inreok.getiserver.domain.collector.repository.CollectionRunRepository
import team.inreok.getiserver.domain.collector.repository.JobSourceRepository
import team.inreok.getiserver.domain.collector.service.CollectionRunQueryService
import java.time.LocalDateTime

@Service
class CollectionRunQueryServiceImpl(
    private val collectionRunRepository: CollectionRunRepository,
    private val collectionRunErrorRepository: CollectionRunErrorRepository,
    private val jobSourceRepository: JobSourceRepository,
) : CollectionRunQueryService {
    @Transactional(readOnly = true)
    override fun search(
        sourceId: Long?,
        status: CollectionRunStatus?,
        startAt: LocalDateTime?,
        endAt: LocalDateTime?,
        pageable: Pageable,
    ): CollectionRunListResponse {
        val page = collectionRunRepository.search(sourceId, status, startAt, endAt, pageable)
        // 항목마다 수집원을 조회하면 N+1이 되므로 한 번에 가져온다(JobService.searchPublic과 동일한 방식).
        val sources = findSourceNames(page.content.map { it.sourceId })

        return CollectionRunListResponse(
            content =
                page.content.map {
                    CollectionRunSummaryResponse.from(
                        it,
                        sources[it.sourceId] ?: UNKNOWN_SOURCE_NAME,
                    )
                },
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            first = page.isFirst,
            last = page.isLast,
        )
    }

    @Transactional(readOnly = true)
    override fun getDetail(runId: Long): CollectionRunDetailResponse {
        val run = findRun(runId)
        val sourceName = findSourceNames(listOf(run.sourceId))[run.sourceId] ?: UNKNOWN_SOURCE_NAME
        val errors = collectionRunErrorRepository.findAllByRunIdOrderByOccurredAtAsc(runId)
        return CollectionRunDetailResponse.from(run, sourceName, errors)
    }

    private fun findRun(runId: Long): CollectionRun =
        collectionRunRepository.findById(runId).orElseThrow {
            CollectionRunNotFoundException(runId)
        }

    private fun findSourceNames(sourceIds: Collection<Long>): Map<Long, String> =
        jobSourceRepository
            .findAllById(sourceIds.distinct())
            .mapNotNull { source -> source.id?.let { it to source.name } }
            .toMap()

    private companion object {
        const val UNKNOWN_SOURCE_NAME = "알 수 없음"
    }
}
