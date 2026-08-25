package team.inreok.getiserver.domain.portfolio.exception

import org.springframework.http.HttpStatus
import team.inreok.getiserver.global.error.ErrorCode

/**
 * Portfolio Domain이 실제로 처리하는 오류만 정의한다(요구사항 §32).
 *
 * 아래 Error Code는 이번 Phase 범위가 아니라 아직 만들지 않았다 -- 실제로 던지는 곳이 생기는
 * Phase에서 추가한다.
 * - `PORTFOLIO_ACCESS_DENIED`: 제출물 File 다운로드 권한(Phase 3, §17)에서 쓴다.
 * - `NO_SUBMISSIONS_TO_EXPORT`: 일괄 다운로드(Phase 5, §24)에서 쓴다.
 */
enum class PortfolioErrorCode(
    override val status: HttpStatus,
    override val defaultMessage: String,
) : ErrorCode {
    PORTFOLIO_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 포트폴리오 수합 요청을 찾을 수 없습니다."),
    TARGET_STUDENT_REQUIRED(HttpStatus.BAD_REQUEST, "제출 대상 학생을 최소 한 명 이상 지정해야 합니다."),
    INVALID_TARGET_STUDENT(HttpStatus.BAD_REQUEST, "제출 대상은 실제 학생만 지정할 수 있습니다."),
    NOT_REQUEST_TARGET(HttpStatus.FORBIDDEN, "해당 수합 요청의 제출 대상이 아닙니다."),
    SUBMISSION_CLOSED(HttpStatus.CONFLICT, "제출 기간이 아니거나 마감되어 제출할 수 없습니다."),
    PORTFOLIO_REQUEST_NOT_EDITABLE(HttpStatus.CONFLICT, "현재 상태에서는 수합 요청을 수정할 수 없습니다."),
    PORTFOLIO_STATUS_TRANSITION_INVALID(HttpStatus.CONFLICT, "허용되지 않은 수합 요청 상태 변경입니다."),
    ;

    override val code: String get() = name
}
