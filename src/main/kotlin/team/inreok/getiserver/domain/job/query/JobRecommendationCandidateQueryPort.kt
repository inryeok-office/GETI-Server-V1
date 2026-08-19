package team.inreok.getiserver.domain.job.query

import org.springframework.modulith.NamedInterface
import java.time.LocalDateTime

/**
 * `recommendation` Module이 추천 후보 공고를 읽는 유일한 공개 계약이다(Recommendation R2, Issue
 * #148). `recommendation`은 이 Interface를 통해서만 Job을 읽고, `Job` Entity나 `JobRepository`를
 * 직접 참조하지 않는다.
 *
 * [JobIndexQueryPort]/[JobApplicationSnapshotQueryPort]처럼 소비 Domain 전용으로 좁힌 별도
 * Port다 -- `search`는 PUBLISHED/CLOSED를 모두 색인 대상으로 보지만, Recommendation은 "오늘
 * 지원 가능한 공고"만 후보로 삼으므로 CLOSED를 candidate 조회 단계에서부터 제외한다(마감된
 * 공고까지 가져와 매번 걸러내지 않는다).
 */
@NamedInterface
interface JobRecommendationCandidateQueryPort {
    /**
     * 삭제되지 않고 PUBLISHED인 공고를 전부 반환한다(Recommendation 후보 Pool). 현재 규모
     * 기준(GETI R1 설계 문서 §17, 활성 공고 약 1,000건)에서는 Keyset Pagination 없이 한 번에
     * 조회해도 충분하다.
     */
    fun findAllPublished(): List<JobRecommendationCandidateSnapshot>
}

@NamedInterface
data class JobRecommendationCandidateSnapshot(
    val jobId: Long,
    val companyId: Long,
    val title: String,
    /** `JobStatus.name`. 이 Port는 항상 PUBLISHED만 반환하지만, Hard Filter가 방어적으로 다시 확인한다. */
    val status: String,
    val targetGrade: Int?,
    val publishedAt: LocalDateTime?,
    val recruitmentEndedAt: LocalDateTime?,
)
