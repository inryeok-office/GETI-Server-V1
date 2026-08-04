package team.inreok.getiserver.domain.collector.service

import org.springframework.data.domain.Pageable
import team.inreok.getiserver.domain.collector.dto.CollectionRunDetailResponse
import team.inreok.getiserver.domain.collector.dto.CollectionRunListResponse
import team.inreok.getiserver.domain.collector.entity.type.CollectionRunStatus
import java.time.LocalDateTime

interface CollectionRunQueryService {
    /** 필터·Pagination 모두 DB Query에서 처리한다(전체 이력을 메모리로 가져오지 않는다). */
    fun search(
        sourceId: Long?,
        status: CollectionRunStatus?,
        startAt: LocalDateTime?,
        endAt: LocalDateTime?,
        pageable: Pageable,
    ): CollectionRunListResponse

    fun getDetail(runId: Long): CollectionRunDetailResponse
}
