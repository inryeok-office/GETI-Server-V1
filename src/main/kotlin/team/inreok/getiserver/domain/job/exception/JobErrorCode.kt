package team.inreok.getiserver.domain.job.exception

import org.springframework.http.HttpStatus
import team.inreok.getiserver.global.error.ErrorCode

/**
 * Job Domain이 실제로 처리하는 오류만 정의한다.
 *
 * 다음 Error Code는 의도적으로 만들지 않았다.
 * - `SORT_NOT_SUPPORTED`: `sort`/`status`를 Enum Parameter로 받으므로 잘못된 값은 Spring의
 *   Type 변환 실패로 이어져 이미 `CommonErrorCode.TYPE_MISMATCH`(400)가 처리한다.
 * - `NOT_JOB_MANAGER`: 담당자 기준이 확정되지 않아 역할(TEACHER/DEVELOPER) 검증만 수행한다.
 * - `JOB_SOURCE_NOT_FOUND`: `job_sources` Table이 없어 공고 출처 API를 구현하지 않았다.
 */
enum class JobErrorCode(
    override val status: HttpStatus,
    override val defaultMessage: String,
) : ErrorCode {
    JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 공고를 찾을 수 없습니다."),
    JOB_NOT_VISIBLE(HttpStatus.FORBIDDEN, "공개되지 않은 공고입니다."),
    JOB_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "공고 정보가 올바르지 않습니다."),
    JOB_STATUS_TRANSITION_INVALID(HttpStatus.CONFLICT, "허용되지 않은 공고 상태 변경입니다."),
    JOB_FORM_REQUIRED(HttpStatus.BAD_REQUEST, "내부 지원 공고는 지원서 양식이 연결되어야 게시할 수 있습니다."),
    JOB_DISCORD_CHANNEL_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "허용되지 않은 Discord 채널입니다."),

    // domain.company.exception.CompanyErrorCode는 Company Module 내부 구현이라 참조할 수 없어
    // 여기 다시 정의한다. 응답에 나가는 code 문자열과 HTTP Status(404)는 Company와 동일하므로
    // Client가 보는 계약은 하나다.
    COMPANY_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 기업을 찾을 수 없습니다."),
    ;

    override val code: String get() = name
}
