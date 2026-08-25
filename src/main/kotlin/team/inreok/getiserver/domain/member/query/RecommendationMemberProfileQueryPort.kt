package team.inreok.getiserver.domain.member.query

import org.springframework.modulith.NamedInterface

/**
 * `recommendation` Module이 회원 단위 추천을 계산할 때 읽는 공개 계약이다(Recommendation R2,
 * Issue #148). `recommendation`은 이 Interface를 통해서만 Member를 읽고, `Member` Entity나
 * `MemberRepository`/`MemberTechStackRepository`를 직접 참조하지 않는다.
 *
 * [MemberApplicantSnapshotQueryPort]와 다른 이유: 그 Port는 기술 스택을 이름(`List<String>`)으로
 * 노출해 지원서 자동입력에는 맞지만, Recommendation Score 계산은 AI Analysis가 이미 매칭해 둔
 * `techStackId`([team.inreok.getiserver.domain.ai.query.AiAnalysisSearchSnapshot.requiredTechStackIds]
 * 등)와 정확한 ID 비교가 필요해 이름 목록으로는 계산할 수 없다.
 */
@NamedInterface
interface RecommendationMemberProfileQueryPort {
    /** 존재하지 않으면 null을 반환한다. */
    fun findById(memberId: Long): RecommendationMemberProfileSnapshot?
}

/**
 * Recommendation Score 계산에 실제로 쓰는 값만 담는다. Score와 무관한 이름/연락처/졸업 여부 같은
 * 개인정보는 담지 않는다(R2 요구사항 "Score에서 사용하지 않는 Field는 Snapshot에 넣지 않는다").
 */
@NamedInterface
data class RecommendationMemberProfileSnapshot(
    val memberId: Long,
    /** `MemberStatus.name` */
    val status: String,
    /** `Job.targetGrade`와 정확히 비교하는 값이다(대상 학년 Hard Filter). */
    val grade: Int?,
    /** GETI Tech Stack Master와 매칭된 회원 보유 기술 ID 집합이다. 중복은 없다. */
    val techStackIds: Set<Long>,
)
