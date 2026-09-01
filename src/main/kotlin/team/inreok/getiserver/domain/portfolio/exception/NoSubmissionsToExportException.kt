package team.inreok.getiserver.domain.portfolio.exception

import team.inreok.getiserver.global.error.BusinessException

/**
 * 관리자가 수합 요청의 제출 자료를 일괄 다운로드할 때 실제로 내려받을 파일이 하나도 없을 때
 * 사용한다(요구사항 §24, Issue #204 Phase 2b).
 *
 * 제출물이 아예 없거나(전원 미제출), 있더라도 연결된 파일이 하나도 없는 경우다. 빈 ZIP을 내려주지
 * 않고 404(NO_SUBMISSIONS_TO_EXPORT)로 명확히 알린다. 이 판정은 Storage 접근 전에 Service가 먼저
 * 수행해, 응답 Byte를 쓰기 시작하기 전에 정상적인 JSON 오류 응답으로 나가게 한다.
 */
class NoSubmissionsToExportException : BusinessException(PortfolioErrorCode.NO_SUBMISSIONS_TO_EXPORT)
