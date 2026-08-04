package team.inreok.getiserver.domain.search.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(description = "검색 색인 운영 명령 요청")
data class SearchActionRequest(
    @param:Schema(description = "실행할 동작")
    @field:NotNull
    val action: SearchAction?,
)
