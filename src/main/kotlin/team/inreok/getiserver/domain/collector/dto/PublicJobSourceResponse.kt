package team.inreok.getiserver.domain.collector.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.collector.entity.JobSource
import team.inreok.getiserver.domain.collector.entity.type.JobSourceType

@Schema(description = "공개 공고 출처 목록 항목. 관리자용 목록과 달리 승인 상태·설정 여부·인증키 관련 정보를 포함하지 않는다.")
data class PublicJobSourceResponse(
    @param:Schema(description = "수집원 ID")
    val sourceId: Long,
    @param:Schema(description = "수집원 이름")
    val name: String,
    @param:Schema(description = "수집원 유형")
    val sourceType: JobSourceType,
    @param:Schema(description = "활성화 여부(운영자가 관리자 API로 변경하는 값과 같다)")
    val active: Boolean,
) {
    companion object {
        fun from(source: JobSource): PublicJobSourceResponse =
            PublicJobSourceResponse(
                sourceId = requireNotNull(source.id),
                name = source.name,
                sourceType = source.sourceType,
                active = source.enabled,
            )
    }
}

@Schema(description = "공개 공고 출처 목록")
data class PublicJobSourceListResponse(
    @param:Schema(description = "수집원 목록")
    val sources: List<PublicJobSourceResponse>,
)
