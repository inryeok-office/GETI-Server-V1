package team.inreok.getiserver.domain.recommendation.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.ai.query.AiAnalysisSearchQueryPort
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
 * [RecommendationGenerationService]의 구현이다(Recommendation R2, Issue #148).
 *
 * Hard Filter(`computeExclusionReason`)와 Score Engine(`calculateScore`)은 순수 함수라 여기서는
 * Query Port 호출 -> 순수 계산 조립 -> Ranking -> Persistence만 담당한다. Candidate/AI 조회를
 * 모두 Batch(회원 1명당 각 1회)로 수행해 Job 개수만큼 반복 조회(N+1)하지 않는다.
 */
@Service
class RecommendationGenerationServiceImpl(
    private val memberProfileQueryPort: RecommendationMemberProfileQueryPort,
    private val jobCandidateQueryPort: JobRecommendationCandidateQueryPort,
    private val aiAnalysisSearchQueryPort: AiAnalysisSearchQueryPort,
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
        val excludedJobIds = memberJobPreferenceRepository.findExcludedJobIdsByMemberId(memberId).toSet()
        val aiSnapshots = aiAnalysisSearchQueryPort.findCompletedByJobIds(candidates.map { it.jobId })
        val now = LocalDateTime.now()

        val scored =
            candidates.mapNotNull { job ->
                val aiSnapshot = aiSnapshots[job.jobId]
                val exclusionReason = computeExclusionReason(job, member, excludedJobIds, aiSnapshot, now)
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
