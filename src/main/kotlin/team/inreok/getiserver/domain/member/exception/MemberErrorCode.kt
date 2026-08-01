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
    ;

    override val code: String get() = name
}
