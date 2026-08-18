package team.inreok.getiserver.domain.recommendation.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.ai.query.AiAnalysisSearchQueryPort
import team.inreok.getiserver.domain.job.access.JobApplicationEligibilityAccessor
import team.inreok.getiserver.domain.job.query.JobRecommendationCandidateQueryPort
import team.inreok.getiserver.domain.member.query.RecommendationMemberProfileQueryPort
import team.inreok.getiserver.domain.recommendation.entity.Recommendation
import team.inreok.getiserver.domain.recommendation.repository.MemberJobPreferenceRepository
import team.inreok.getiserver.domain.recommendation.repository.RecommendationRepository
import team.inreok.getiserver.domain.recommendation.service.RankedRecommendation
import team.inreok.getiserver.domain.recommendation.service.RecommendationGenerationService
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * [RecommendationGenerationService]의 구현이다(Recommendation R2 Issue #148, Application 지원
 * 가능 여부 연동 R5 Issue #165).
 *
 * Hard Filter(`computeExclusionReason`)와 Score Engine(`calculateScore`)은 순수 함수라 여기서는
 * Query Port 호출 -> 순수 계산 조립 -> Ranking -> Persistence만 담당한다. Candidate/AI/Application
 * Eligibility 조회를 모두 Batch(회원 1명당 각 1회)로 수행해 Job 개수만큼 반복 조회(N+1)하지 않는다.
 * `job.access.JobApplicationEligibilityAccessor`는 `job`이 소유하고 `application`이 구현하는
 * SPI다 -- `recommendation`은 이 계약을 통해서만 지원 여부를 읽고 `application` Package를 직접
 * 참조하지 않는다.
 */
@Service
class RecommendationGenerationServiceImpl(
    private val memberProfileQueryPort: RecommendationMemberProfileQueryPort,
    private val jobCandidateQueryPort: JobRecommendationCandidateQueryPort,
    private val aiAnalysisSearchQueryPort: AiAnalysisSearchQueryPort,
    private val jobApplicationEligibilityAccessor: JobApplicationEligibilityAccessor,
    private val memberJobPreferenceRepository: MemberJobPreferenceRepository,
    private val recommendationRepository: RecommendationRepository,
    private val objectMapper: ObjectMapper,
) : RecommendationGenerationService {
    @Transactional
    override fun generateForMember(
        memberId: Long,
        limit: Int,
    ): List<RankedRecommendation> {
        val member = memberProfileQueryPort.findById(memberId) ?: return emptyList()
        val candidates = jobCandidateQueryPort.findAllPublished()
        val candidateJobIds = candidates.map { it.jobId }.toSet()
        val excludedJobIds = memberJobPreferenceRepository.findExcludedJobIdsByMemberId(memberId).toSet()
        val bookmarkedJobIds = memberJobPreferenceRepository.findBookmarkedJobIdsByMemberId(memberId).toSet()
        val aiSnapshots = aiAnalysisSearchQueryPort.findCompletedByJobIds(candidateJobIds.toList())
        val eligibilityByJobId = jobApplicationEligibilityAccessor.findAllByJobIds(candidateJobIds, memberId)
        val now = LocalDateTime.now()

        val scored =
            candidates.mapNotNull { job ->
                val aiSnapshot = aiSnapshots[job.jobId]
                val hasActiveApplication = eligibilityByJobId[job.jobId]?.eligibilityReason == ALREADY_APPLIED_REASON
                val exclusionReason =
                    computeExclusionReason(
                        job,
                        member,
                        excludedJobIds,
                        bookmarkedJobIds,
                        aiSnapshot,
                        hasActiveApplication,
                        now,
                    )
                if (exclusionReason != null) return@mapNotNull null
                val scoreResult = calculateScore(member.techStackIds, aiSnapshot) ?: return@mapNotNull null
                ScoredRecommendationCandidate(job, scoreResult)
            }

        val ranked = rank(scored, limit)
        replaceTodayRecommendations(memberId, ranked)
        return ranked
    }

    private fun replaceTodayRecommendations(
        memberId: Long,
        ranked: List<RankedRecommendation>,
    ) {
        val today = LocalDate.now()
        recommendationRepository.deleteAllByMemberIdAndRecommendationDate(memberId, today)
        if (ranked.isEmpty()) return
        val entities =
            ranked.map { candidate ->
                Recommendation(
                    memberId = memberId,
                    jobId = candidate.jobId,
                    recommendationDate = today,
                    score = BigDecimal(candidate.score),
                    suitability = suitabilityOf(candidate.score),
                    rank = candidate.rank,
                    algorithmVersion = RECOMMENDATION_ALGORITHM_VERSION,
                ).apply { reasons = objectMapper.writeValueAsString(candidate.reasons) }
            }
        recommendationRepository.saveAll(entities)
    }
}

// `job.access.JobApplicationEligibilityAccessSnapshot.eligibilityReason`은
// `application.entity.type.JobApplicationEligibilityReason`의 Enum 이름 문자열이다(그 값 자체를
// `recommendation`에 노출하지 않기 위한 의도적 설계, Accessor Class 주석 참고). `recommendation`도
// `application` Enum을 직접 참조하지 않으므로 이름 문자열로만 비교한다.
private const val ALREADY_APPLIED_REASON = "ALREADY_APPLIED"
