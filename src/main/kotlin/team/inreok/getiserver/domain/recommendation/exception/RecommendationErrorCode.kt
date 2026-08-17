package team.inreok.getiserver.domain.recommendation.exception

import org.springframework.http.HttpStatus
import team.inreok.getiserver.global.error.ErrorCode

/**
 * Recommendation Domain이 실제로 처리하는 오류만 정의한다(R3, Issue #152).
 *
 * 다음은 의도적으로 만들지 않았다.
 * - 권한 오류: `SecurityConfig`가 `/api/v1/recommendations` 이하 전체 경로를 STUDENT Role로
 *   이미 제한해 Role 자체가 다른 요청은 `CommonErrorCode.FORBIDDEN`으로 거부된다.
 * - "관심 없음 중복"/"관심 없음 해제 대상 없음": 요구사항상 멱등 성공(200)이라 오류가 아니다.
 * - "추천 OFF 상태에서 조회": 오류가 아니라 정상 200 DISABLED 응답이다.
 */
enum class RecommendationErrorCode(
    override val status: HttpStatus,
    override val defaultMessage: String,
) : ErrorCode {
    // domain.job.exception.JobErrorCode는 Job Module 내부 구현이라 참조할 수 없어(Spring
    // Modulith) 여기 다시 정의한다. 응답에 나가는 code 문자열과 HTTP Status(404)는 Job과
    // 동일하므로 Client가 보는 계약은 하나다(JobErrorCode의 COMPANY_NOT_FOUND와 같은 관례).
    JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 공고를 찾을 수 없습니다."),
    ;

    override val code: String get() = name
}
