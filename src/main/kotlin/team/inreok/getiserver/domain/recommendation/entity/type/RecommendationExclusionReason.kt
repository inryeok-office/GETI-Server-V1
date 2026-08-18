package team.inreok.getiserver.domain.recommendation.entity.type

/**
 * Recommendation Hard Filter가 후보를 제외한 이유다(R2 설계, `JobApplicationEligibilityReason`과
 * 같은 목적 -- 각 분기를 Test에서 직접 검증할 수 있게 이유를 값으로 남긴다). DB에 저장하지 않는다
 * -- 제외된 후보는 애초에 `recommendations`에 Row로 남지 않는다(R1 결정, "DB에 반드시 저장할
 * 필요는 없음").
 */
enum class RecommendationExclusionReason {
    /** 삭제됐거나 PUBLISHED가 아닌 공고(CLOSED/DRAFT 포함). */
    JOB_NOT_PUBLISHED,

    /** `recruitmentEndedAt`이 지난 공고. Job 도메인에 자동 마감 Scheduler가 없어 status만으로는
     * 판단할 수 없다(R1 설계 근거). */
    RECRUITMENT_ENDED,

    /** `Job.targetGrade`가 회원의 `grade`와 다름(둘 다 null이 아닌 경우만 비교). */
    TARGET_GRADE_MISMATCH,

    /** 회원이 해당 공고를 관심 없음(`MemberJobPreference.exclusion`) 처리함. */
    NOT_INTERESTED,

    /** 회원이 해당 공고를 이미 북마크함(`MemberJobPreference.bookmarked`). Notion 기능명세
     * "맞춤 추천 페이지 -- 이미 북마크한 공고는 추천 목록에서 제외"(Issue #155). */
    ALREADY_BOOKMARKED,

    /** 회원이 해당 공고에 이미 활성 지원서를 가짐(R5, Issue #165).
     * `job.access.JobApplicationEligibilityAccessor`가 계산한
     * `JobApplicationEligibilityAccessSnapshot.applicationId != null` 여부를 옮긴 값이다 --
     * Application의 `JobApplicationEligibilityReason` Enum(`ALREADY_APPLIED`)을 직접 참조하지
     * 않기 위해 이름은 같지만 별도 독립적인 값으로 둔다(`RecommendationExclusionReason`은
     * Application 어떤 Package도 모른다). `eligibilityReason` 문자열이 아니라 `applicationId`
     * 기준인 이유는 `RecommendationCandidateFilter.computeExclusionReason`의 `hasActiveApplication`
     * KDoc 참고(코드리뷰 반영, PR #166). */
    ALREADY_APPLIED,

    /** AI Analysis의 `highSchoolGraduateFit`이 UNSUITABLE. */
    HIGH_SCHOOL_UNSUITABLE,
}
