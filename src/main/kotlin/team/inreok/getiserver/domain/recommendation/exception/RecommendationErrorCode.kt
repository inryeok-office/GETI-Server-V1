package team.inreok.getiserver.domain.recommendation.exception

import org.springframework.http.HttpStatus
import team.inreok.getiserver.global.error.ErrorCode

/**
 * Recommendation Domain이 실제로 처리하는 오류만 정의한다(R3 Issue #152, Notion 계약 정합성
 * Issue #155).
 *
 * 다음은 의도적으로 만들지 않았다.
 * - 권한 오류: `SecurityConfig`가 `/api/v1/me/job-recommendations`,
 *   `/api/v1/me/recommendation-exclusions`, `/api/v1/recommendations/settings` 하위 전체
 *   경로를 STUDENT Role로 이미 제한해 Role 자체가 다른 요청은 `CommonErrorCode.FORBIDDEN`으로 거부된다.
 * - "추천 OFF 상태에서 조회": 오류가 아니라 정상 200 DISABLED 응답이다.
 * - `RECOMMENDATION_PROFILE_INCOMPLETE`(Notion API 명세 400): "프로필 미완성"의 확정 기준을
 *   찾지 못해(어떤 값이 없으면 미완성인지 정의가 없음) 이번 PR에서 구현하지 않았다(PR 본문
 *   DECISION_REQUIRED 참고, 근거 없이 판정 기준을 임의로 만들지 않는다).
 */
enum class RecommendationErrorCode(
    override val status: HttpStatus,
    override val defaultMessage: String,
) : ErrorCode {
    // domain.job.exception.JobErrorCode는 Job Module 내부 구현이라 참조할 수 없어(Spring
    // Modulith) 여기 다시 정의한다. 응답에 나가는 code 문자열과 HTTP Status(404)는 Job과
    // 동일하므로 Client가 보는 계약은 하나다(JobErrorCode의 COMPANY_NOT_FOUND와 같은 관례).
    JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 공고를 찾을 수 없습니다."),

    /** Notion 계약(`POST /api/v1/me/recommendation-exclusions`): 같은 공고에 이미 관심 없음이
     * 설정돼 있는데 다시 등록을 요청함. */
    EXCLUSION_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 관심 없음으로 설정된 공고입니다."),

    /** Notion 계약(`DELETE /api/v1/me/recommendation-exclusions/{exclusionId}`): 대상이 없거나
     * 요청자 본인 소유가 아님(소유권 노출 방지를 위해 두 경우를 구분하지 않는다). */
    EXCLUSION_NOT_FOUND(HttpStatus.NOT_FOUND, "관심 없음 설정을 찾을 수 없습니다."),

    /** Issue #170: 같은 공고를 이미 북마크했는데 다시 등록을 요청함 -- 같은 Entity를 쓰는
     * `EXCLUSION_ALREADY_EXISTS`와 동일한 정책(멱등이 아니라 409)을 따른다. */
    BOOKMARK_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 북마크한 공고입니다."),

    /** Issue #170: 북마크하지 않은 공고를 해제하려 함(Row 자체가 없거나 `bookmarked=false`인
     * 경우 모두 포함) -- `EXCLUSION_NOT_FOUND`와 동일한 정책을 따른다. */
    BOOKMARK_NOT_FOUND(HttpStatus.NOT_FOUND, "북마크한 공고를 찾을 수 없습니다."),

    /** 관심 없음/북마크 등록이 같은 (memberId, jobId) Row를 동시에 새로 만들려는 다른 요청과
     * 경합해 `uk_member_job_preferences_member_job` UNIQUE 제약을 위반함(코드리뷰 반영, PR
     * #178). `member_job_preferences`는 관심 없음과 북마크가 같은 Row/제약을 공유하므로, 이
     * 경합에서 진 요청은 상대가 같은 종류의 등록이었는지 알 수 없다 -- `EXCLUSION_ALREADY_EXISTS`/
     * `BOOKMARK_ALREADY_EXISTS`로 단정하면 실제로는 등록되지 않은 상태인데도 "이미 등록됨"으로
     * 잘못 응답할 수 있다(`saveExclusionPreference`/`saveBookmarkPreference` KDoc 참고). Client는
     * 이 Code를 받으면 현재 상태를 다시 조회하거나 재시도할 수 있다. */
    PREFERENCE_WRITE_CONFLICT(HttpStatus.CONFLICT, "다른 요청과 동시에 처리되어 실패했습니다. 다시 시도해 주세요."),

    /** Notion 기능명세("추천 설정 -- 졸업생에게는 추천 설정을 제공하지 않는다"). `AcademicStatus`가
     * ENROLLED가 아니면(졸업·자퇴) 차단한다 -- `JobApplicationEligibility`/`ProgramEligibility`의
     * `academicStatus != "ENROLLED"` 판정과 같은 관례를 따른다. */
    NOT_ENROLLED(HttpStatus.BAD_REQUEST, "재학 중인 학생만 추천 설정을 변경할 수 있습니다."),
    ;

    override val code: String get() = name
}
