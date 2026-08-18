package team.inreok.getiserver.domain.recommendation.service.impl

import team.inreok.getiserver.domain.ai.query.AiAnalysisSearchSnapshot
import team.inreok.getiserver.domain.job.query.JobRecommendationCandidateSnapshot
import team.inreok.getiserver.domain.member.query.RecommendationMemberProfileSnapshot
import team.inreok.getiserver.domain.recommendation.entity.type.RecommendationExclusionReason
import team.inreok.getiserver.domain.recommendation.entity.type.RecommendationExclusionReason.ALREADY_APPLIED
import team.inreok.getiserver.domain.recommendation.entity.type.RecommendationExclusionReason.ALREADY_BOOKMARKED
import team.inreok.getiserver.domain.recommendation.entity.type.RecommendationExclusionReason.HIGH_SCHOOL_UNSUITABLE
import team.inreok.getiserver.domain.recommendation.entity.type.RecommendationExclusionReason.JOB_NOT_PUBLISHED
import team.inreok.getiserver.domain.recommendation.entity.type.RecommendationExclusionReason.NOT_INTERESTED
import team.inreok.getiserver.domain.recommendation.entity.type.RecommendationExclusionReason.RECRUITMENT_ENDED
import team.inreok.getiserver.domain.recommendation.entity.type.RecommendationExclusionReason.TARGET_GRADE_MISMATCH
import java.time.LocalDateTime

/**
 * Recommendation Hard Filter다(R2 요구사항, Recommendation R1 설계 §5/§11). Repository/Port 호출이
 * 없는 순수 함수로 둬 모든 분기를 Unit Test에서 직접 검증할 수 있게 한다
 * (`JobApplicationEligibility.computeEligibilityReason`과 같은 이유).
 *
 * `null`을 반환하면 Score 계산 대상이다.
 *
 * [bookmarkedJobIds]는 Notion 기능명세("이미 북마크한 공고는 추천 목록에서 제외") 반영이다(Issue
 * #155) -- [excludedJobIds](관심 없음)와 별개 개념이라 별도 Parameter로 받는다.
 *
 * [hasActiveApplication]은 `job.access.JobApplicationEligibilityAccessor`가 계산한
 * `eligibilityReason == "ALREADY_APPLIED"` 여부다(R5, Issue #165). Application의 다른
 * `eligibilityReason`(`NOT_INTERNAL`/`NOT_ENROLLED`/`NOT_TARGET_GRADE`/`BEFORE_START`/
 * `AFTER_END`/`JOB_NOT_PUBLISHED`)은 여기서 쓰지 않는다 -- `JOB_NOT_PUBLISHED`/`AFTER_END`/
 * `NOT_TARGET_GRADE`는 이미 위에서 동일한 조건을 직접 검사하고, `NOT_ENROLLED`는 R4 Scheduler의
 * 대상 회원 필터링에서 이미 걸러지며, `NOT_INTERNAL`(외부 지원 공고)까지 배제하면 외부 지원 공고
 * 전체가 추천에서 사라지는 회귀가 생긴다(Issue #165 PR 본문 지원 상태 Matrix 참고). 그래서
 * Application의 `canApply` Boolean 전체가 아니라 `ALREADY_APPLIED` 여부 하나만 신호로 받는다 --
 * 이 함수 자체는 Application의 어떤 타입도 모른다(순수 `Boolean`).
 */
@Suppress("ReturnCount")
fun computeExclusionReason(
    job: JobRecommendationCandidateSnapshot,
    member: RecommendationMemberProfileSnapshot,
    excludedJobIds: Set<Long>,
    bookmarkedJobIds: Set<Long>,
    aiSnapshot: AiAnalysisSearchSnapshot?,
    hasActiveApplication: Boolean,
    now: LocalDateTime,
): RecommendationExclusionReason? {
    if (job.status != "PUBLISHED") return JOB_NOT_PUBLISHED
    if (job.recruitmentEndedAt != null && now.isAfter(job.recruitmentEndedAt)) return RECRUITMENT_ENDED
    if (job.targetGrade != null && member.grade != null && job.targetGrade != member.grade) {
        return TARGET_GRADE_MISMATCH
    }
    if (hasActiveApplication) return ALREADY_APPLIED
    if (job.jobId in excludedJobIds) return NOT_INTERESTED
    if (job.jobId in bookmarkedJobIds) return ALREADY_BOOKMARKED
    if (aiSnapshot?.highSchoolGraduateFit == "UNSUITABLE") return HIGH_SCHOOL_UNSUITABLE
    return null
}
