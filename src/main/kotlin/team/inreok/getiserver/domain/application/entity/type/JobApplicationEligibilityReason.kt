package team.inreok.getiserver.domain.application.entity.type

// 요구사항 4·7절 Enum 그대로. 값과 판정 순서는 docs/application/application-domain-plan.md §6.5를
// 따른다.
enum class JobApplicationEligibilityReason {
    AVAILABLE,
    NOT_INTERNAL,
    NOT_ENROLLED,
    NOT_TARGET_GRADE,
    BEFORE_START,
    AFTER_END,
    ALREADY_APPLIED,
    JOB_NOT_PUBLISHED,
}
