package team.inreok.getiserver.domain.ai.entity.type

/**
 * `job_ai_analyses.status`의 Phase 1 확정 계약이다(2026-08 GETI Notion/Figma 대조 기준). 과거
 * Persistence Skeleton 시점의 `PARTIAL`/`INSUFFICIENT`는 Phase 1 계약에 없고 Production
 * Column에도 CHECK 제약이 없어 실사용 이력이 없으므로 이번에 제거했다.
 */
enum class AiStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
}
