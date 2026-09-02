package team.inreok.getiserver.domain.portfolio.repository

/**
 * 요청(request)별 집계 개수를 배치로 조회할 때 쓰는 Projection이다. 목록 응답의 `targetCount`,
 * `submittedCount`를 항목마다 단건 조회하면 N+1이 되므로(요구사항 §35), 이번 Page의 requestId를
 * 한 번에 `GROUP BY`로 집계해 이 형태로 돌려받는다.
 */
interface RequestCountProjection {
    val requestId: Long
    val count: Long
}
