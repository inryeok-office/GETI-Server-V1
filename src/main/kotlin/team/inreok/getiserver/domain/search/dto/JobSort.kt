package team.inreok.getiserver.domain.search.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 공개 공고 목록의 정렬 기준이다. Entity/Document Field 이름을 그대로 Query Parameter로
 * 노출하지 않기 위해 Enum으로 제한한다(`Pageable`이 함께 들고 오는 `sort`는 무시한다).
 *
 * 모든 정렬은 공고 ID 내림차순을 보조 정렬로 갖는다(Elasticsearch Query가 적용한다, Issue #69).
 * 정렬 값이 같은 공고가 여러 건일 때 Page 사이에서 순서가 흔들려 중복·누락이 생기는 것을 막기
 * 위함이다.
 */
@Schema(description = "공고 목록 정렬 기준. 모든 정렬은 동일 값에서 공고 ID 내림차순으로 안정화된다.")
enum class JobSort {
    @Schema(description = "최근 게시순")
    LATEST,

    @Schema(description = "마감 임박순. 마감일이 없는 공고는 뒤로 보낸다")
    DEADLINE,

    @Schema(description = "조회수 많은순")
    VIEWS,
}
