package team.inreok.getiserver.domain.portfolio.exception

import team.inreok.getiserver.global.error.BusinessException

/**
 * 학생이 제출할 수 없는 시점(요청이 아직 공개 전이 아니라 이미 마감(CLOSED)됐거나 제출 마감
 * 시각(dueAt)을 지난 상태)에 제출을 시도할 때 사용한다(요구사항 §14).
 *
 * 아직 공개되지 않은(DRAFT) 요청은 대상 학생에게도 존재를 노출하지 않아야 하므로 이 예외가 아니라
 * 404(PORTFOLIO_REQUEST_NOT_FOUND)로 처리한다.
 */
class SubmissionClosedException : BusinessException(PortfolioErrorCode.SUBMISSION_CLOSED)
