package team.inreok.getiserver.domain.member.exception

import org.springframework.http.HttpStatus
import team.inreok.getiserver.global.error.ErrorCode

enum class MemberErrorCode(
    override val status: HttpStatus,
    override val defaultMessage: String,
) : ErrorCode {
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 회원을 찾을 수 없습니다."),
    PROFILE_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "프로필 요청 값이 올바르지 않습니다."),
    PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "내 프로필을 찾을 수 없습니다."),
    MAJOR_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 전공을 찾을 수 없습니다."),
    DUPLICATE_MAJOR(HttpStatus.CONFLICT, "전공 목록에 중복된 값이 있습니다."),
    TECH_STACK_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 기술 스택을 찾을 수 없습니다."),
    NAME_REQUIRED(HttpStatus.BAD_REQUEST, "검색할 이름을 입력해야 합니다."),
    NOT_A_STUDENT(HttpStatus.FORBIDDEN, "학생만 접근할 수 있습니다."),

    /**
     * 학생 프로필 조회는 학생뿐 아니라 교사·개발자에게도 열려 있어(Issue #114) [NOT_A_STUDENT]로는
     * 거부 사유를 정확히 설명할 수 없다. 실제로 이 Code를 받는 것은 Role을 아직 부여받지 못한
     * 승인 대기 교직원이다.
     */
    PROFILE_VIEW_FORBIDDEN(HttpStatus.FORBIDDEN, "학생 프로필을 조회할 권한이 없습니다."),
    OAUTH_EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 다른 방식으로 가입된 이메일입니다."),
    MEMBER_LOGIN_NOT_ALLOWED(HttpStatus.FORBIDDEN, "로그인이 허용되지 않는 회원 상태입니다."),
    MEMBER_NOT_PENDING(HttpStatus.CONFLICT, "승인 대기 상태의 회원만 처리할 수 있습니다."),
    MEMBER_NOT_APPROVAL_TARGET(HttpStatus.BAD_REQUEST, "교직원 승인 대상 회원이 아닙니다."),
    MEMBER_REJECTION_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "거절 사유를 입력해야 합니다."),
    ;

    override val code: String get() = name
}
