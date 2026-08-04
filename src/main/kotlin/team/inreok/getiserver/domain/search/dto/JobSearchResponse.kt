package team.inreok.getiserver.domain.search.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "공고 검색·목록 결과. Spring Data Page 규약과 동일하게 page는 0부터 시작한다.")
data class JobSearchResponse(
    @param:Schema(description = "검색 결과 목록")
    val content: List<JobSummaryResponse>,
    @param:Schema(description = "현재 Page 번호(0부터 시작)", example = "0")
    val page: Int,
    @param:Schema(description = "Page당 개수", example = "20")
    val size: Int,
    @param:Schema(description = "전체 결과 개수", example = "42")
    val totalElements: Long,
    @param:Schema(description = "전체 Page 수", example = "3")
    val totalPages: Int,
    @param:Schema(description = "첫 Page 여부", example = "true")
    val first: Boolean,
    @param:Schema(description = "마지막 Page 여부", example = "false")
    val last: Boolean,
)
