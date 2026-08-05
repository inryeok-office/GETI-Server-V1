package team.inreok.getiserver.domain.application.exception

import org.springframework.http.HttpStatus
import team.inreok.getiserver.global.error.ErrorCode

// Phase 1(Form) 범위에서 실제로 발생하는 Error Code만 정의한다. 요구사항 24절의 Application
// 생성·조회/답변·제출/학생·교사 Action Error Code는 해당 Phase(2~4) 구현 시점에 추가한다.
enum class ApplicationErrorCode(
    override val status: HttpStatus,
    override val defaultMessage: String,
) : ErrorCode {
    FORM_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 신청 양식을 찾을 수 없습니다."),
    NOT_FORM_OWNER(HttpStatus.FORBIDDEN, "본인이 소유한 양식만 조회할 수 있습니다."),
    FORM_NOT_OWNED(HttpStatus.FORBIDDEN, "본인이 소유한 양식만 변경할 수 있습니다."),
    FORM_ARCHIVED(HttpStatus.CONFLICT, "보관된 양식은 수정할 수 없습니다."),
    FORM_ACTION_INVALID(HttpStatus.BAD_REQUEST, "허용되지 않는 양식 Action입니다."),
    INVALID_FORM_FIELD(HttpStatus.BAD_REQUEST, "양식 필드 구성이 올바르지 않습니다."),
    ;

    override val code: String get() = name
}
