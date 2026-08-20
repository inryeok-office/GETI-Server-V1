package team.inreok.getiserver.domain.job.query

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.modulith.NamedInterface
import team.inreok.getiserver.domain.job.entity.type.PostingType

/** Recommendation Domain이 본인 북마크 공고를 조회할 때 사용하는 Job 검색 조건이다. */
@NamedInterface
data class JobBookmarkQuery(
    val query: String?,
    val postingType: PostingType?,
    /** CompanyType은 Company Module 내부 Entity Type을 Job Query 계약에 노출하지 않도록 이름으로 전달한다. */
    val companyType: String?,
    val sort: JobBookmarkSort,
)

@NamedInterface
@Schema(description = "내 북마크 공고 목록 정렬 기준")
enum class JobBookmarkSort {
    @Schema(description = "최근 게시순")
    LATEST,

    @Schema(description = "마감 임박순. 마감일이 없는 공고는 뒤로 보낸다")
    DEADLINE,

    @Schema(description = "조회수 많은순")
    VIEWS,
}
