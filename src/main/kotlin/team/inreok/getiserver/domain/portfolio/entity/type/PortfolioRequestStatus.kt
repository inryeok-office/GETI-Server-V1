package team.inreok.getiserver.domain.portfolio.entity.type

/**
 * 포트폴리오 수합 요청의 상태다(요구사항 §7).
 *
 * ```text
 * DRAFT     -> PUBLISHED | DELETED
 * PUBLISHED -> CLOSED    | DELETED
 * CLOSED    -> DELETED
 * ```
 *
 * DRAFT는 작성 중(학생에게 노출되지 않음), PUBLISHED는 공개되어 대상 학생이 조회·제출할 수 있는
 * 상태, CLOSED는 마감(제출 불가), DELETED는 Soft Delete다. 삭제는 실제 행을 지우지 않고 상태
 * 전이로 처리해 이력을 보존한다(§7 "삭제는 Hard Delete보다 Soft Delete / 상태 전이 우선").
 */
enum class PortfolioRequestStatus {
    DRAFT,
    PUBLISHED,
    CLOSED,
    DELETED,
}
