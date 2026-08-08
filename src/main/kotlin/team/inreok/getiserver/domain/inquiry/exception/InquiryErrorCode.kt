package team.inreok.getiserver.domain.inquiry.exception

import org.springframework.http.HttpStatus
import team.inreok.getiserver.global.error.ErrorCode

// GETI Inquiry 도메인 개발 요구사항 53절 목록 중 Phase 2(등록·내 목록·상세 조회), Phase 3(관리자
// 목록·담당자 지정·상태 변경), Phase 4(답변 등록) 범위에서 실제로 발생하는 Error Code만 정의한다
// (Program의 ProgramErrorCode와 동일한 관례).
enum class InquiryErrorCode(
    override val status: HttpStatus,
    override val defaultMessage: String,
) : ErrorCode {
    INQUIRY_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 문의를 찾을 수 없습니다."),

    // 학생·교사가 본인이 작성하지 않은 문의를 조회하려 할 때(요구사항 16절). 개발자는 이 검증을
    // 우회한다(전체 관리 조회, 요구사항 51절 권한 Matrix).
    INQUIRY_ACCESS_DENIED(HttpStatus.FORBIDDEN, "본인이 작성한 문의만 조회할 수 있습니다."),

    // 담당자로 지정하려는 회원이 role=DEVELOPER AND status=ACTIVE를 동시에 만족하지 않을 때
    // (요구사항 32절, 사용자 확인 완료). 회원 자체가 없는 경우는 별도로 MEMBER_NOT_FOUND(404)다.
    INVALID_INQUIRY_ASSIGNEE(HttpStatus.BAD_REQUEST, "담당자로 지정할 수 없는 회원입니다."),

    // 담당자로 지정하려는 회원 id가 존재하지 않을 때(요구사항 32절/53절).
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 회원을 찾을 수 없습니다."),

    // 확정된 상태 전이 Matrix(RECEIVED->IN_PROGRESS/ANSWERED, IN_PROGRESS->ANSWERED/CLOSED,
    // ANSWERED->CLOSED, CLOSED는 최종)를 벗어난 요청과, 답변이 없는데 status=ANSWERED로 직접
    // 지정하려는 요청 모두 이 Code를 쓴다(요구사항 35절~37절, 사용자 확인 완료). 원본 요구사항
    // 문서 53절이 이 Code를 400으로 분류해(Program의 상태 전이 오류가 409인 것과 다름) 그대로
    // 따랐다.
    INQUIRY_STATUS_INVALID(HttpStatus.BAD_REQUEST, "허용되지 않은 상태 변경입니다."),

    // CLOSED는 최종 상태라 그 이후에는 답변을 등록할 수 없다(요구사항 §36/§48, 사용자 확인
    // 완료). "존재하는 자원의 현재 상태와 충돌"이라 Program/Application의 상태 충돌 Error
    // Code(FORM_ARCHIVED, PROGRAM_STATUS_TRANSITION_NOT_ALLOWED 등)와 동일하게 409로 분류한다 --
    // INQUIRY_STATUS_INVALID(400, PATCH .../status 전용)와는 원인이 다르다.
    INQUIRY_ALREADY_CLOSED(HttpStatus.CONFLICT, "이미 종료된 문의에는 답변을 등록할 수 없습니다."),
    ;

    override val code: String get() = name
}
