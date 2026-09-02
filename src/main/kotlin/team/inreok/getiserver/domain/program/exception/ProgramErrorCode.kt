package team.inreok.getiserver.domain.program.exception

import org.springframework.http.HttpStatus
import team.inreok.getiserver.global.error.ErrorCode

// 원본 요구사항 문서("GETI-Server Program 도메인 전체 개발 요구사항") 23절 목록 중 Phase
// 1~3(등록·수정·상태 관리, 목록·상세·Eligibility, 신청·취소·동시성) 범위에서 실제로 발생하는
// Error Code만 정의한다. Phase 4 이후(PROGRAM_ACCESS_DENIED, NO_APPLICATIONS_TO_EXPORT,
// 빈자리 구독 관련 코드)는 해당 Phase 구현 시점에 추가한다.
//
// HTTP Status는 문서에 명시되지 않은 항목이 많아 기존 Job/Application 도메인 관례를 따라
// 판단했다. 요청 형식만으로 판단 가능한 위반(INVALID_*, *_REQUIRED)은 400, 서버의 현재 상태에
// 따라 달라지는 충돌(정원 초과, 상태 전이, 중복 신청 등)은 409, 존재하지 않음은 404, 삭제된
// 리소스 접근은 410으로 맞췄다(JobErrorCode/ApplicationErrorCode와 동일한 관례).
enum class ProgramErrorCode(
    override val status: HttpStatus,
    override val defaultMessage: String,
) : ErrorCode {
    // 등록·수정 (Phase 1)
    INVALID_PROGRAM_PERIOD(HttpStatus.BAD_REQUEST, "일정 또는 신청 기간이 올바르지 않습니다."),
    INVALID_CAPACITY(HttpStatus.BAD_REQUEST, "모집 정원은 1명 이상이어야 합니다."),
    TARGET_GRADE_REQUIRED(HttpStatus.BAD_REQUEST, "대상 학년을 1개 이상 선택해야 합니다."),

    // 원본 문서 23절에는 없지만 6절 "필수 검증"(대상 학년은 1,2,3만 허용/중복 금지)을 실제로
    // 처리하려면 TARGET_GRADE_REQUIRED(값이 비어 있음)와 구분되는 코드가 필요해 추가했다.
    INVALID_TARGET_GRADE(HttpStatus.BAD_REQUEST, "대상 학년은 1, 2, 3 중 하나여야 하며 중복될 수 없습니다."),
    DISCORD_CHANNEL_REQUIRED(HttpStatus.BAD_REQUEST, "게시하려면 Discord 채널을 선택해야 합니다."),
    DISCORD_CHANNEL_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "허용되지 않은 Discord 채널입니다."),
    PROGRAM_FORM_NOT_LINKABLE(HttpStatus.BAD_REQUEST, "연결할 수 없는 신청 양식입니다."),

    // 원본 문서 23절에는 없지만 6절 "필수 검증"(제목 공백, 본문 공백, 위치 필수, 게시 시 일정
    // 필수) 중 다른 전용 코드가 없는 항목을 JobErrorCode.JOB_VALIDATION_FAILED와 동일한 방식으로
    // 처리하기 위해 추가했다.
    PROGRAM_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "프로그램 정보가 올바르지 않습니다."),
    PROGRAM_MANAGE_FORBIDDEN(HttpStatus.FORBIDDEN, "프로그램 등록자·담당 교사·개발자만 관리할 수 있습니다."),
    PROGRAM_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 프로그램을 찾을 수 없습니다."),
    CAPACITY_BELOW_CURRENT_APPLICANTS(HttpStatus.CONFLICT, "정원을 현재 활성 신청 인원보다 적게 설정할 수 없습니다."),
    PROGRAM_FIELDS_IMMUTABLE(HttpStatus.CONFLICT, "게시된 프로그램은 이 필드를 변경할 수 없습니다."),

    // 상태 변경 (Phase 1)
    PROGRAM_STATUS_TRANSITION_NOT_ALLOWED(HttpStatus.CONFLICT, "허용되지 않은 프로그램 상태 변경입니다."),
    PROGRAM_REOPEN_NOT_ALLOWED(HttpStatus.CONFLICT, "마감된 프로그램은 다시 게시할 수 없습니다."),
    PROGRAM_RESTORE_NOT_ALLOWED(HttpStatus.CONFLICT, "삭제된 프로그램은 복구할 수 없습니다."),

    // 상세 조회 (Phase 2)
    PROGRAM_DELETED(HttpStatus.GONE, "삭제된 프로그램입니다."),

    // 신청·취소 (Phase 3)
    NOT_ENROLLED(HttpStatus.BAD_REQUEST, "재학 중인 학생만 신청할 수 있습니다."),
    NOT_TARGET_GRADE(HttpStatus.BAD_REQUEST, "신청 대상 학년이 아닙니다."),
    PROGRAM_NOT_OPEN(HttpStatus.BAD_REQUEST, "아직 신청 기간이 아닙니다."),
    PROGRAM_CLOSED(HttpStatus.BAD_REQUEST, "신청이 마감되었습니다."),
    PROGRAM_FULL(HttpStatus.CONFLICT, "정원이 마감되었습니다."),
    ALREADY_APPLIED(HttpStatus.CONFLICT, "이미 이 프로그램에 신청한 이력이 있습니다."),
    ACTIVE_APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "취소할 수 있는 활성 신청이 없습니다."),
    PROGRAM_ACTION_NOT_AVAILABLE(HttpStatus.CONFLICT, "현재 상태에서는 이 작업을 할 수 없습니다."),
    ;

    override val code: String get() = name
}
