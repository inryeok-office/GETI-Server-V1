package team.inreok.getiserver.domain.recommendation.service.impl

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.ai.query.AiAnalysisSearchQueryPort
import team.inreok.getiserver.domain.job.access.JobApplicationEligibilityAccessor
import team.inreok.getiserver.domain.job.query.JobRecommendationCandidateQueryPort
import team.inreok.getiserver.domain.member.query.RecommendationMemberProfileQueryPort
import team.inreok.getiserver.domain.recommendation.entity.Recommendation
import team.inreok.getiserver.domain.recommendation.entity.type.ExclusionType
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
 * 가능 여부 연동 R5 Issue #165, SIMILAR_JOBS 유사 공고 확장 R6 Issue #167).
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
    // SIMILAR_JOBS 유사 판정 Threshold다(R6, Issue #167 DECISION_REQUIRED 확정 -- Jaccard
    // Similarity 0.6 이상을 SIMILAR로 본다). `RecommendationSimilarityEvaluator`가 이 값을
    // Magic Number로 갖지 않도록 Configuration으로 분리했다(R4 Top-N/Cron과 같은 관례).
    @param:Value("\${app.recommendation.similarity.tech-stack-threshold:0.6}") private val similarityThreshold: Double,
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
        // SIMILAR_JOBS로 관심 없음 처리한 기준(Source) Job이다(R6, Issue #167) -- THIS_JOB은
        // 이미 excludedJobIds가 그 Job 자체를 걸러내므로 여기서는 SIMILAR_JOBS만 좁혀 조회한다.
        val similarJobsSourceIds =
            memberJobPreferenceRepository
                .findJobIdsByMemberIdAndExclusionType(memberId, ExclusionType.SIMILAR_JOBS)
                .toSet()
        // Candidate와 SIMILAR_JOBS 기준 Job(현재 후보 Pool 밖의 CLOSED Job일 수 있음)의 AI 분석을
        // 한 번의 Batch 호출로 함께 조회한다 -- 두 번 나눠 부르지 않는다(N+1 방지).
        val aiSnapshots =
            aiAnalysisSearchQueryPort.findCompletedByJobIds((candidateJobIds + similarJobsSourceIds).toList())
        val eligibilityByJobId = jobApplicationEligibilityAccessor.findAllByJobIds(candidateJobIds, memberId)
        // 기준 Job의 AI 분석이 없거나 COMPLETED가 아니면(aiSnapshots에 없음) 유사 공고 확장을
        // 하지 않는다(보수적 Fallback, Issue #167 §4) -- 그런 기준 Job은 여기서 자연히 빠진다.
        val similarJobsSourceTechStackSets = similarJobsSourceIds.mapNotNull { aiSnapshots[it]?.let(::techStackSetOf) }
        val now = LocalDateTime.now()

        val scored =
            candidates.mapNotNull { job ->
                val aiSnapshot = aiSnapshots[job.jobId]
                // eligibilityReason 문자열("ALREADY_APPLIED")과 비교하지 않는다 -- Application의
                // computeEligibilityReason은 hasActiveApplication -> ALREADY_APPLIED보다
                // !hasActiveLinkedForm -> JOB_NOT_PUBLISHED를 먼저 평가해, 이미 활성 지원서가
                // 있어도 연결 Form이 비활성화되면 reason이 "JOB_NOT_PUBLISHED"로 나올 수 있다
                // (코드리뷰 반영, PR #166). applicationId는 그 분기 순서와 무관하게 활성 지원서
                // 유무만으로 채워지므로(JobApplicationEligibilityAccessorImpl 참고) 더 안전한
                // 신호다.
                val hasActiveApplication = eligibilityByJobId[job.jobId]?.applicationId != null
                // 여러 SIMILAR_JOBS 기준 Job 중 하나라도 이 후보와 유사하면 제외한다(Issue #167
                // §5). 비교는 Memory에서만 수행하고 추가 DB 조회는 없다.
                val isSimilarToExcludedJob =
                    isSimilarToAnySource(aiSnapshot, similarJobsSourceTechStackSets, similarityThreshold)
                val exclusionReason =
                    computeExclusionReason(
                        job,
                        member,
                        excludedJobIds,
                        bookmarkedJobIds,
                        aiSnapshot,
                        hasActiveApplication,
                        isSimilarToExcludedJob,
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
