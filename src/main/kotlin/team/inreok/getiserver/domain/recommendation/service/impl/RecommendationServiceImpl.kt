package team.inreok.getiserver.domain.recommendation.service.impl

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.company.query.CompanyQuery
import team.inreok.getiserver.domain.company.query.CompanySummary
import team.inreok.getiserver.domain.job.query.JobRecommendationCandidateQueryPort
import team.inreok.getiserver.domain.job.query.JobRecommendationCandidateSnapshot
import team.inreok.getiserver.domain.recommendation.dto.RecommendationItemResponse
import team.inreok.getiserver.domain.recommendation.dto.RecommendationJobResponse
import team.inreok.getiserver.domain.recommendation.dto.RecommendationListResponse
import team.inreok.getiserver.domain.recommendation.dto.RecommendationReasonResponse
import team.inreok.getiserver.domain.recommendation.dto.RecommendationSettingResponse
import team.inreok.getiserver.domain.recommendation.dto.RecommendationStatus
import team.inreok.getiserver.domain.recommendation.entity.MemberJobPreference
import team.inreok.getiserver.domain.recommendation.entity.MemberJobPreferenceId
import team.inreok.getiserver.domain.recommendation.entity.Recommendation
import team.inreok.getiserver.domain.recommendation.entity.RecommendationPreference
import team.inreok.getiserver.domain.recommendation.entity.type.ExclusionType
import team.inreok.getiserver.domain.recommendation.exception.RecommendationJobNotFoundException
import team.inreok.getiserver.domain.recommendation.repository.MemberJobPreferenceRepository
import team.inreok.getiserver.domain.recommendation.repository.RecommendationPreferenceRepository
import team.inreok.getiserver.domain.recommendation.repository.RecommendationRepository
import team.inreok.getiserver.domain.recommendation.service.RecommendationReason
import team.inreok.getiserver.domain.recommendation.service.RecommendationService
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate

/**
 * [RecommendationService]의 구현이다(Recommendation R3, Issue #152). R2
 * ([team.inreok.getiserver.domain.recommendation.service.RecommendationGenerationService])가
 * 이미 계산·저장한 결과를 읽고 사용자 설정을 바꿀 뿐, Score/Hard Filter/Ranking을 다시 계산하지
 * 않는다.
 */
@Service
class RecommendationServiceImpl(
    private val recommendationRepository: RecommendationRepository,
    private val recommendationPreferenceRepository: RecommendationPreferenceRepository,
    private val memberJobPreferenceRepository: MemberJobPreferenceRepository,
    private val jobRecommendationCandidateQueryPort: JobRecommendationCandidateQueryPort,
    private val companyQuery: CompanyQuery,
    private val objectMapper: ObjectMapper,
) : RecommendationService {
    private val log = LoggerFactory.getLogger(RecommendationServiceImpl::class.java)

    @Transactional(readOnly = true)
    override fun getMyRecommendations(memberId: Long): RecommendationListResponse {
        if (!isEnabled(memberId)) return DISABLED_RESPONSE

        val rows = recommendationRepository.findAllByMemberIdAndRecommendationDateOrderByRank(memberId, LocalDate.now())
        return if (rows.isEmpty()) {
            RecommendationListResponse(
                enabled = true,
                status = RecommendationStatus.EMPTY,
                generatedAt = null,
                items = emptyList(),
            )
        } else {
            RecommendationListResponse(
                enabled = true,
                status = RecommendationStatus.READY,
                generatedAt = generatedAtOf(rows),
                items = toItemResponses(rows),
            )
        }
    }

    // 오늘자 Batch는 한 Transaction 안에서 함께 저장돼(RecommendationGenerationServiceImpl.
    // replaceTodayRecommendations) createdAt이 사실상 같은 시각이지만, 정확한 값을 보장하려고 Rank
    // 재정렬 없이 이미 가져온 List에서 MAX만 계산한다(추가 Query 없음).
    private fun generatedAtOf(rows: List<Recommendation>) =
        rows.maxOfOrNull { requireNotNull(it.createdAt) { "저장된 Recommendation은 createdAt을 가져야 합니다." } }

    @Transactional
    override fun updateSetting(
        memberId: Long,
        enabled: Boolean,
    ): RecommendationSettingResponse {
        val preference =
            recommendationPreferenceRepository.findByMemberId(memberId)
                ?: RecommendationPreference(memberId = memberId, enabled = enabled)
        preference.enabled = enabled
        // GenerationType.IDENTITY라 save()가 이미 즉시 Insert/Update를 Flush한다(신규 Row도
        // 생성된 id를 알아야 하므로). updatedAt(@UpdateTimestamp)도 이 시점에 채워진다.
        val saved = recommendationPreferenceRepository.save(preference)
        return RecommendationSettingResponse(
            enabled = saved.enabled,
            updatedAt = requireNotNull(saved.updatedAt) { "저장된 RecommendationPreference는 updatedAt을 가져야 합니다." },
        )
    }

    @Transactional
    override fun markNotInterested(
        memberId: Long,
        jobId: Long,
    ) {
        requireJobExists(jobId)
        val id = MemberJobPreferenceId(memberId, jobId)
        val preference = memberJobPreferenceRepository.findById(id).orElse(null) ?: MemberJobPreference(id = id)
        preference.exclusion = ExclusionType.THIS_JOB
        memberJobPreferenceRepository.save(preference)
        // 요구사항 "관심 없음 즉시 Recommendation 결과 처리" -- 조회는 항상 오늘자 결과만
        // 보여주므로 날짜 제한 없이 이 (memberId, jobId) 조합을 통째로 지워도 안전하고, 다음
        // 생성부터는 Hard Filter(NOT_INTERESTED)가 이 Job을 다시 걸러낸다.
        recommendationRepository.deleteAllByMemberIdAndJobId(memberId, jobId)
    }

    @Transactional
    override fun removeNotInterested(
        memberId: Long,
        jobId: Long,
    ) {
        val id = MemberJobPreferenceId(memberId, jobId)
        val preference = memberJobPreferenceRepository.findById(id).orElse(null) ?: return
        preference.exclusion = null
        // bookmarked가 true인 Row(향후 북마크 기능)를 관심 없음 해제만으로 지우면 그 정보가
        // 함께 사라진다. exclusion도 bookmarked도 이제 없으면(=이 Row가 더 이상 어떤 의미도
        // 갖지 않으면) Row 자체를 지워 불필요한 빈 Row가 쌓이지 않게 한다.
        // if/else의 두 분기가 서로 다른 반환 Type(delete: Unit, save: MemberJobPreference)을
        // 갖는 채로 함수의 마지막 문장이 되면, Kotlin이 이 if를 Unit으로 강제 변환하는 과정에서
        // JpaRepository.save()의 Java 반환값에 실제로 쓰지도 않는 Not-Null 단정을 걸어 Mock이
        // 돌려주는 null에 NPE를 던진다(Kotlin/Java 상호운용 알려진 동작). 두 분기 모두 Unit을
        // 명시해 이 강제 변환 자체를 없앤다.
        if (!preference.bookmarked) {
            memberJobPreferenceRepository.delete(preference)
        } else {
            memberJobPreferenceRepository.save(preference)
            Unit
        }
    }

    private fun isEnabled(memberId: Long): Boolean =
        recommendationPreferenceRepository.findByMemberId(memberId)?.enabled ?: DEFAULT_ENABLED

    private fun requireJobExists(jobId: Long) {
        if (jobRecommendationCandidateQueryPort.findAllByIds(setOf(jobId)).isEmpty()) {
            throw RecommendationJobNotFoundException(jobId)
        }
    }

    private fun toItemResponses(rows: List<Recommendation>): List<RecommendationItemResponse> {
        val jobIds = rows.map { it.jobId }.toSet()
        val jobs = jobRecommendationCandidateQueryPort.findAllByIds(jobIds)
        val companyIds = jobs.values.map { it.companyId }.toSet()
        val companies = companyQuery.findActiveSummaries(companyIds)
        // 추천 생성 이후 Job이 삭제됐으면(Soft Delete) jobs Map에 없다 -- 삭제된 공고를 담아
        // 반환하지 않고 해당 항목만 건너뛴다(호출부가 판단할 표시 규칙이 없어 가장 안전한 기본값).
        return rows.mapNotNull { row -> toItemResponse(row, jobs[row.jobId] ?: return@mapNotNull null, companies) }
    }

    private fun toItemResponse(
        row: Recommendation,
        job: JobRecommendationCandidateSnapshot,
        companies: Map<Long, CompanySummary>,
    ): RecommendationItemResponse {
        val company = companies[job.companyId]
        return RecommendationItemResponse(
            job =
                RecommendationJobResponse(
                    jobId = job.jobId,
                    title = job.title,
                    companyName = company?.name,
                    companyLogoUrl = company?.logoUrl,
                    recruitmentEndedAt = job.recruitmentEndedAt,
                ),
            score = row.score.toInt(),
            suitability = row.suitability,
            rank = row.rank,
            reasons = readReasons(row),
        )
    }

    /**
     * `reasons` JSON 파싱 실패는 이 Row 하나만 이유 없이 보여주고 나머지 목록은 그대로
     * 반환한다 -- 저장된 Reason 하나가 손상됐다고 전체 추천 조회가 500이 되면 안 된다(요구사항
     * "파싱 실패 시 전체 Recommendation 조회가 500이 되는 구조 피하기"). 다만 조용히 숨기지
     * 않고 원인을 추적할 수 있도록 warn Log를 남긴다.
     */
    private fun readReasons(row: Recommendation): List<RecommendationReasonResponse> {
        val json = row.reasons ?: return emptyList()
        return runCatching {
            objectMapper
                .readValue(json, Array<RecommendationReason>::class.java)
                .map { RecommendationReasonResponse(it.type, it.matchedCount, it.totalCount) }
        }.getOrElse { ex ->
            log.warn(
                "Recommendation reasons 파싱 실패(recommendationId={}, memberId={}, jobId={})",
                row.id,
                row.memberId,
                row.jobId,
                ex,
            )
            emptyList()
        }
    }

    private companion object {
        const val DEFAULT_ENABLED = false
        val DISABLED_RESPONSE =
            RecommendationListResponse(
                enabled = false,
                status = RecommendationStatus.DISABLED,
                generatedAt = null,
                items = emptyList(),
            )
    }
}
