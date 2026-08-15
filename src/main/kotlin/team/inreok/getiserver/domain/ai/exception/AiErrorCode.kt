package team.inreok.getiserver.domain.ai.exception

import org.springframework.http.HttpStatus
import team.inreok.getiserver.global.error.ErrorCode

/**
 * AI Analysis Domain이 실제로 처리하는 오류만 정의한다(AI Analysis Phase 1, Issue #132).
 *
 * `JOB_NOT_FOUND`는 `domain.job.exception.JobErrorCode.JOB_NOT_FOUND`와 응답 계약(code
 * 문자열, HTTP Status)이 같지만, Job Module 내부 구현이라 참조할 수 없어 여기 다시 정의한다
 * (`JobErrorCode`가 `COMPANY_NOT_FOUND`를 다시 정의하는 것과 같은 방식).
 */
enum class AiErrorCode(
    override val status: HttpStatus,
    override val defaultMessage: String,
) : ErrorCode {
    JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "AI 분석 대상 공고를 찾을 수 없습니다."),
    AI_ALREADY_PROCESSING(HttpStatus.CONFLICT, "이미 AI 분석이 진행 중입니다."),
    AI_REANALYSIS_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "수동 재분석 가능 횟수를 모두 사용했습니다."),
    AI_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI 분석 기능을 현재 사용할 수 없습니다."),
    ;

    override val code: String get() = name
}
