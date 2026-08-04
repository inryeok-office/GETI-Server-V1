package team.inreok.getiserver.domain.collector.entity.type

// domain.operation.entity.type.OperationStatus(PENDING/PROCESSING/COMPLETED/FAILED)를 재사용하지
// 않는다. 그 Enum은 job_collection_runs/async_operations가 이미 공유하는 값 집합으로 문서화되어
// 있고(docs/architecture/erd.md), 이번 API 명세가 요구하는 PARTIAL_SUCCESS/CANCELED 값이 없다.
// 기존 공유 Enum을 임의로 확장하면 operation 도메인(AsyncOperation)에도 영향을 주므로, Collector
// 실행 상태 전용 Enum을 새로 둔다(최종 보고의 "문서 불일치 및 결정" 참고).
enum class CollectionRunStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILED,
    CANCELED,
}
