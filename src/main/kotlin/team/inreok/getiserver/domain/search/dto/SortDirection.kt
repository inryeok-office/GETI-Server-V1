package team.inreok.getiserver.domain.search.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * `sort`가 가리키는 정렬 기준의 방향을 뒤집는다(Issue #69, 기능명세서의 "오름차순·내림차순"
 * 요구사항). 지정하지 않으면 각 [JobSort]의 기본 방향을 사용한다 — `LATEST`/`VIEWS`는
 * 내림차순, `DEADLINE`은 오름차순이 기본이다.
 */
@Schema(description = "정렬 방향. 생략하면 각 정렬 기준의 기본 방향을 사용한다.")
enum class SortDirection {
    ASC,
    DESC,
}
