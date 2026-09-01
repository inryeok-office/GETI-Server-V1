package team.inreok.getiserver.domain.recommendation.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "북마크 등록 요청")
data class BookmarkCreateRequest(
    @param:Schema(description = "북마크할 공고 ID", example = "1")
    val jobId: Long,
)
