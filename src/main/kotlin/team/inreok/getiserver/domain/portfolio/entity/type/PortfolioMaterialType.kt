package team.inreok.getiserver.domain.portfolio.entity.type

/**
 * 관리자 제출 현황(요구사항 §19)에서 학생이 어떤 형태의 자료를 제출했는지 나타낸다(Issue #204 Phase 2b).
 *
 * `portfolio_submissions`에 이 값을 저장하는 Column은 없다 -- 제출물이 실제로 가진 것(연결된 파일,
 * `portfolioUrl`)에서 조회 시점에 파생한다. 저장하지 않는 이유는 파일 연결·URL이 재제출로 바뀌면
 * 별도 Column과 실제 상태가 어긋날 수 있기 때문이다(Source of Truth를 하나로 유지).
 *
 * 연결된 파일도 없고 `portfolioUrl`도 없는 제출물(예: 메모만 남긴 임시저장)은 어느 종류에도 해당하지
 * 않아 응답에서 null로 표현한다.
 */
enum class PortfolioMaterialType {
    /** 연결된 파일만 있고 URL은 없다. */
    FILE,

    /** `portfolioUrl`만 있고 연결된 파일은 없다. */
    URL,

    /** 파일과 URL을 모두 제출했다. */
    BOTH,
}
