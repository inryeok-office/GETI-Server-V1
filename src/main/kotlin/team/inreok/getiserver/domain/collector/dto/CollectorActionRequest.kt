package team.inreok.getiserver.domain.collector.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

// action은 Notion API 명세서와 저장소 어디에도 CollectorAction 값이 확정되어 있지 않아
// 임의로 Enum을 만들지 않고 String으로 받는다. Controller는 이 요청을 항상
// CollectorActionNotSupportedException(501)으로 거부한다(Issue #62 Blocker).
@Schema(description = "수동 수집·동기화 실행 요청. CollectorAction 값이 확정되지 않아 현재는 항상 501로 거부된다.")
data class CollectorActionRequest(
    @param:Schema(description = "실행 종류(CollectorAction 값 미확정)", example = "COLLECT")
    @field:NotNull
    val action: String?,
    @param:Schema(description = "대상 수집원 ID 목록")
    @field:NotEmpty
    val sourceIds: List<Long>?,
)
