package team.inreok.getiserver.domain.recommendation.service.impl

import team.inreok.getiserver.domain.ai.query.AiAnalysisSearchSnapshot
import team.inreok.getiserver.domain.job.query.JobRecommendationCandidateSnapshot
import team.inreok.getiserver.domain.member.query.RecommendationMemberProfileSnapshot
import team.inreok.getiserver.domain.recommendation.entity.type.RecommendationExclusionReason
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
 * `null`을 반환하면 Score 계산 대상이다. Application `canApply`/이미 지원한 공고 Filter는 R2
 * 범위가 아니다 -- Application 공개 Query Contract가 아직 없어 R5에서 연결한다(R1 설계 §15/§41).
 */
@Suppress("ReturnCount")
fun computeExclusionReason(
    job: JobRecommendationCandidateSnapshot,
    member: RecommendationMemberProfileSnapshot,
    excludedJobIds: Set<Long>,
    aiSnapshot: AiAnalysisSearchSnapshot?,
    now: LocalDateTime,
): RecommendationExclusionReason? {
    if (job.status != "PUBLISHED") return JOB_NOT_PUBLISHED
    if (job.recruitmentEndedAt != null && now.isAfter(job.recruitmentEndedAt)) return RECRUITMENT_ENDED
    if (job.targetGrade != null && member.grade != null && job.targetGrade != member.grade) {
        return TARGET_GRADE_MISMATCH
    }
    if (job.jobId in excludedJobIds) return NOT_INTERESTED
    if (aiSnapshot?.highSchoolGraduateFit == "UNSUITABLE") return HIGH_SCHOOL_UNSUITABLE
    return null
}
