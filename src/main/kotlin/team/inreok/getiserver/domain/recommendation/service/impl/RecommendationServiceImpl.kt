package team.inreok.getiserver.domain.recommendation.service.impl

import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.company.query.CompanyQuery
import team.inreok.getiserver.domain.company.query.CompanySummary
import team.inreok.getiserver.domain.job.query.JobRecommendationCandidateQueryPort
import team.inreok.getiserver.domain.job.query.JobRecommendationCandidateSnapshot
import team.inreok.getiserver.domain.member.query.MemberApplicantSnapshotQueryPort
import team.inreok.getiserver.domain.recommendation.dto.RecommendationExclusionListResponse
import team.inreok.getiserver.domain.recommendation.dto.RecommendationExclusionResponse
import team.inreok.getiserver.domain.recommendation.dto.RecommendationItemResponse
import team.inreok.getiserver.domain.recommendation.dto.RecommendationListResponse
import team.inreok.getiserver.domain.recommendation.dto.RecommendationSettingResponse
import team.inreok.getiserver.domain.recommendation.dto.RecommendationStatus
import team.inreok.getiserver.domain.recommendation.entity.MemberJobPreference
import team.inreok.getiserver.domain.recommendation.entity.Recommendation
import team.inreok.getiserver.domain.recommendation.entity.type.ExclusionType
import team.inreok.getiserver.domain.recommendation.entity.type.SuitabilityLevel
import team.inreok.getiserver.domain.recommendation.exception.RecommendationExclusionAlreadyExistsException
import team.inreok.getiserver.domain.recommendation.exception.RecommendationExclusionNotFoundException
import team.inreok.getiserver.domain.recommendation.exception.RecommendationJobNotFoundException
import team.inreok.getiserver.domain.recommendation.exception.RecommendationNotEnrolledException
import team.inreok.getiserver.domain.recommendation.repository.MemberJobPreferenceRepository
import team.inreok.getiserver.domain.recommendation.repository.RecommendationPreferenceRepository
import team.inreok.getiserver.domain.recommendation.repository.RecommendationRepository
import team.inreok.getiserver.domain.recommendation.service.RecommendationService
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * [RecommendationService]의 구현이다(R3 Issue #152, Notion 계약 정합성 Issue #155). R2
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
    private val memberApplicantSnapshotQueryPort: MemberApplicantSnapshotQueryPort,
    private val objectMapper: ObjectMapper,
) : RecommendationService {
    private val log = LoggerFactory.getLogger(RecommendationServiceImpl::class.java)

    @Transactional(readOnly = true)
    override fun getMyRecommendations(
        memberId: Long,
        suitabilityLevel: SuitabilityLevel?,
        pageable: Pageable,
    ): RecommendationListResponse {
        if (!isEnabled(memberId)) return disabledResponse(pageable)

        val today = LocalDate.now()
        val page =
            recommendationRepository.findAllByMemberIdAndRecommendationDate(
                memberId,
                today,
                suitabilityLevel,
                pageable,
            )
        val generatedAt = recommendationRepository.findMaxCreatedAtByMemberIdAndRecommendationDate(memberId, today)
        val status = if (generatedAt == null) RecommendationStatus.EMPTY else RecommendationStatus.READY
        return RecommendationListResponse.of(
            enabled = true,
            status = status,
            generatedAt = generatedAt,
            // R4 Daily Scheduler가 없어 다음 생성 예정 시각을 계산할 근거가 없다(DTO 문서 참고).
            nextGenerationAt = null,
            page = toItemResponsePage(page, memberId),
        )
    }

    private fun disabledResponse(pageable: Pageable) =
        RecommendationListResponse.of(
            enabled = false,
            status = RecommendationStatus.DISABLED,
            generatedAt = null,
            nextGenerationAt = null,
            page = Page.empty(pageable),
        )

    @Transactional
    override fun updateSetting(
        memberId: Long,
        enabled: Boolean,
    ): RecommendationSettingResponse {
        requireEnrolled(memberId)
        // find-then-save 2단계 대신 Upsert 하나로 처리해 동시 최초 설정 요청에서도 멱등을
        // 보장한다(코드리뷰 반영, RecommendationPreferenceRepository.upsert KDoc 참고).
        recommendationPreferenceRepository.upsert(memberId, enabled)
        val saved =
            requireNotNull(recommendationPreferenceRepository.findByMemberId(memberId)) {
                "Upsert 직후에는 RecommendationPreference를 찾을 수 있어야 합니다."
            }
        return RecommendationSettingResponse(
            enabled = saved.enabled,
            updatedAt = requireNotNull(saved.updatedAt) { "저장된 RecommendationPreference는 updatedAt을 가져야 합니다." },
        )
    }

    private fun requireEnrolled(memberId: Long) {
        val academicStatus = memberApplicantSnapshotQueryPort.findById(memberId)?.academicStatus
        if (academicStatus != "ENROLLED") throw RecommendationNotEnrolledException()
    }

    @Transactional
    override fun registerExclusion(
        memberId: Long,
        jobId: Long,
        exclusionType: ExclusionType,
    ): RecommendationExclusionResponse {
        val job = requireJobExists(jobId)
        if (memberJobPreferenceRepository.existsByMemberIdAndJobIdAndExclusionIsNotNull(memberId, jobId)) {
            throw RecommendationExclusionAlreadyExistsException(jobId)
        }
        val preference =
            memberJobPreferenceRepository.findByMemberIdAndJobId(memberId, jobId)
                ?: MemberJobPreference(memberId = memberId, jobId = jobId)
        val now = LocalDateTime.now()
        preference.exclusion = exclusionType
        preference.exclusionCreatedAt = now
        val saved = memberJobPreferenceRepository.save(preference)
        // 요구사항 "관심 없음 즉시 Recommendation 결과 처리" -- 조회는 항상 오늘자 결과만
        // 보여주므로 날짜 제한 없이 이 (memberId, jobId) 조합을 통째로 지워도 안전하고, 다음
        // 생성부터는 Hard Filter(NOT_INTERESTED)가 이 Job을 다시 걸러낸다.
        recommendationRepository.deleteAllByMemberIdAndJobId(memberId, jobId)

        val company = companyQuery.findActiveSummary(job.companyId, memberId)
        return RecommendationExclusionResponse(
            exclusionId = requireNotNull(saved.id) { "저장된 MemberJobPreference는 id를 가져야 합니다." },
            job = buildRecommendationJobResponse(job, company, bookmarked = saved.bookmarked),
            exclusionType = exclusionType,
            createdAt = requireNotNull(saved.exclusionCreatedAt) { "관심 없음을 저장했으면 exclusionCreatedAt이 있어야 합니다." },
        )
    }

    @Transactional(readOnly = true)
    override fun listExclusions(
        memberId: Long,
        exclusionType: ExclusionType?,
        pageable: Pageable,
    ): RecommendationExclusionListResponse {
        val page = memberJobPreferenceRepository.findAllExclusionsByMemberId(memberId, exclusionType, pageable)
        val jobIds = page.content.map { it.jobId }.toSet()
        val jobs = jobRecommendationCandidateQueryPort.findAllByIds(jobIds)
        val companyIds = jobs.values.map { it.companyId }.toSet()
        val companies = companyQuery.findActiveSummaries(companyIds, memberId)

        val items =
            page.content.mapNotNull { preference ->
                val job = jobs[preference.jobId] ?: return@mapNotNull null
                RecommendationExclusionResponse(
                    exclusionId = requireNotNull(preference.id) { "저장된 MemberJobPreference는 id를 가져야 합니다." },
                    job = buildRecommendationJobResponse(job, companies[job.companyId], preference.bookmarked),
                    exclusionType =
                        requireNotNull(preference.exclusion) { "관심 없음 목록 조회 결과는 exclusion이 있어야 합니다." },
                    createdAt =
                        requireNotNull(preference.exclusionCreatedAt) {
                            "관심 없음 목록 조회 결과는 exclusionCreatedAt이 있어야 합니다."
                        },
                )
            }
        return RecommendationExclusionListResponse.of(PageImpl(items, pageable, page.totalElements))
    }

    @Transactional
    override fun removeExclusion(
        memberId: Long,
        exclusionId: Long,
    ) {
        val preference =
            memberJobPreferenceRepository.findByIdAndMemberId(exclusionId, memberId)
                ?: throw RecommendationExclusionNotFoundException(exclusionId)
        preference.exclusion = null
        preference.exclusionCreatedAt = null
        // bookmarked가 true인 Row(북마크 기능)를 관심 없음 해제만으로 지우면 그 정보가 함께
        // 사라진다. exclusion도 bookmarked도 이제 없으면(=이 Row가 더 이상 어떤 의미도 갖지
        // 않으면) Row 자체를 지워 불필요한 빈 Row가 쌓이지 않게 한다.
        if (!preference.bookmarked) {
            memberJobPreferenceRepository.delete(preference)
        } else {
            memberJobPreferenceRepository.save(preference)
            Unit
        }
    }

    private fun isEnabled(memberId: Long): Boolean =
        recommendationPreferenceRepository.findByMemberId(memberId)?.enabled ?: DEFAULT_ENABLED

    private fun requireJobExists(jobId: Long): JobRecommendationCandidateSnapshot =
        jobRecommendationCandidateQueryPort.findAllByIds(setOf(jobId))[jobId]
            ?: throw RecommendationJobNotFoundException(jobId)

    // memberId(requesterId)를 함께 넘겨야 CompanyQuery.findActiveSummary(ries)가 로고 URL을
    // 실제로 발급한다(코드리뷰 반영) -- requesterId를 생략하면 색인·Discord 같은 시스템 문맥으로
    // 간주해 logoUrl이 항상 null이라, 조회 API처럼 인증된 요청자가 있는 호출부는 반드시 전달해야
    // 한다.
    private fun toItemResponsePage(
        page: Page<Recommendation>,
        memberId: Long,
    ): Page<RecommendationItemResponse> {
        val rows = page.content
        val jobIds = rows.map { it.jobId }.toSet()
        val jobs = jobRecommendationCandidateQueryPort.findAllByIds(jobIds)
        val companyIds = jobs.values.map { it.companyId }.toSet()
        val companies = companyQuery.findActiveSummaries(companyIds, memberId)
        val bookmarkedJobIds =
            memberJobPreferenceRepository
                .findBookmarkedJobIdsByMemberIdAndJobIdIn(
                    memberId,
                    jobIds,
                ).toSet()

        // 추천 생성 이후 Job이 삭제됐으면(Soft Delete) jobs Map에 없다 -- 삭제된 공고를 담아
        // 반환하지 않고 해당 항목만 건너뛴다(호출부가 판단할 표시 규칙이 없어 가장 안전한 기본값).
        // 건너뛴 항목만큼 이 Page의 content가 원래 totalElements보다 적게 보일 수 있다 --
        // Recommendation Row 자체의 개수(Filter 이전 삭제 여부와 무관)를 이미 totalElements로
        // 쓰고 있어 별도 보정을 하지 않는다(R3의 기존 결정과 동일한 절충, 드문 경합 상황).
        val items =
            rows.mapNotNull { row ->
                val job = jobs[row.jobId] ?: return@mapNotNull null
                toItemResponse(row, job, companies, bookmarkedJobIds)
            }
        return PageImpl(items, page.pageable, page.totalElements)
    }

    private fun toItemResponse(
        row: Recommendation,
        job: JobRecommendationCandidateSnapshot,
        companies: Map<Long, CompanySummary>,
        bookmarkedJobIds: Set<Long>,
    ): RecommendationItemResponse =
        RecommendationItemResponse(
            recommendationId = requireNotNull(row.id) { "저장된 Recommendation은 id를 가져야 합니다." },
            job =
                buildRecommendationJobResponse(
                    job,
                    companies[job.companyId],
                    bookmarked =
                        job.jobId in bookmarkedJobIds,
                ),
            score = row.score.toInt(),
            suitabilityLevel = row.suitability,
            rank = row.rank,
            reasons = parseRecommendationReasons(row, objectMapper, log),
            generatedAt = requireNotNull(row.createdAt) { "저장된 Recommendation은 createdAt을 가져야 합니다." },
        )

    private companion object {
        const val DEFAULT_ENABLED = false
    }
}
