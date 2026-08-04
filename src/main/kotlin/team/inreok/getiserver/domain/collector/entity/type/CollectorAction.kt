package team.inreok.getiserver.domain.collector.entity.type

// 최신 API 명세(POST /api/v1/admin/collector-actions)에서 확정된 값이다. COLLECT는 수동 즉시
// 수집, SYNC는 마감 등 기존 공고 상태 동기화를 의미한다(Issue #62). Scheduler가 매일 실행하는
// 자동 수집도 의미상 SYNC로 기록한다.
enum class CollectorAction {
    COLLECT,
    SYNC,
}
