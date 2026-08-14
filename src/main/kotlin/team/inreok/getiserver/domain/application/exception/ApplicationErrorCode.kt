package team.inreok.getiserver.domain.application.exception

import org.springframework.http.HttpStatus
import team.inreok.getiserver.global.error.ErrorCode

// Phase 1(Form)·Phase 2(공고-양식 연결, 지원가능여부, 초안·임시저장)·Phase 3(학생 제출·수정요청·
// 재제출·철회) 범위에서 실제로 발생하는 Error Code만 정의한다. 요구사항 24절의 나머지 Application
// Error Code(교사 Action 등)는 해당 Phase(4) 구현 시점에 추가한다.
enum class ApplicationErrorCode(
    override val status: HttpStatus,
    override val defaultMessage: String,
) : ErrorCode {
    // Form (Phase 1)
    FORM_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 신청 양식을 찾을 수 없습니다."),
    NOT_FORM_OWNER(HttpStatus.FORBIDDEN, "본인이 소유한 양식만 조회할 수 있습니다."),
    FORM_NOT_OWNED(HttpStatus.FORBIDDEN, "본인이 소유한 양식만 변경할 수 있습니다."),
    FORM_ARCHIVED(HttpStatus.CONFLICT, "보관된 양식은 수정할 수 없습니다."),
    FORM_ACTION_INVALID(HttpStatus.BAD_REQUEST, "허용되지 않는 양식 Action입니다."),
    INVALID_FORM_FIELD(HttpStatus.BAD_REQUEST, "양식 필드 구성이 올바르지 않습니다."),

    // 공고-양식 연결, 지원가능여부, 초안·임시저장 (Phase 2)
    JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 공고를 찾을 수 없습니다."),
    JOB_APPLICATION_METHOD_NOT_INTERNAL(HttpStatus.BAD_REQUEST, "교내 지원 방식(INTERNAL) 공고에만 양식을 연결할 수 있습니다."),
    FORM_NOT_ACTIVE(HttpStatus.BAD_REQUEST, "ACTIVE 상태의 양식만 공고에 연결할 수 있습니다."),
    JOB_MANAGE_FORBIDDEN(HttpStatus.FORBIDDEN, "공고 등록자·담당 교사·개발자만 양식을 연결할 수 있습니다."),
    JOB_NOT_APPLICABLE(HttpStatus.BAD_REQUEST, "현재 지원할 수 없는 공고입니다."),
    ACTIVE_APPLICATION_EXISTS(HttpStatus.CONFLICT, "이미 이 공고에 진행 중인 지원서가 있습니다."),
    APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 지원서를 찾을 수 없습니다."),
    APPLICATION_ACCESS_FORBIDDEN(HttpStatus.FORBIDDEN, "본인의 지원서만 접근할 수 있습니다."),
    APPLICATION_ACTION_NOT_AVAILABLE(HttpStatus.CONFLICT, "현재 상태에서는 이 작업을 할 수 없습니다."),

    // 학생 제출·수정요청·재제출·철회 (Phase 3)
    APPLICATION_REQUIRED_ANSWER_MISSING(HttpStatus.BAD_REQUEST, "필수 항목에 대한 답변이 누락되었습니다."),
    ;

    override val code: String get() = name
}
