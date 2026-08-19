package team.inreok.getiserver.domain.recommendation.service.impl

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.ai.query.AiAnalysisSearchQueryPort
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
import team.inreok.getiserver.domain.recommendation.entity.type.RecommendationGenerationStatus
import team.inreok.getiserver.domain.recommendation.entity.type.SuitabilityLevel
import team.inreok.getiserver.domain.recommendation.exception.RecommendationExclusionAlreadyExistsException
import team.inreok.getiserver.domain.recommendation.exception.RecommendationExclusionNotFoundException
import team.inreok.getiserver.domain.recommendation.exception.RecommendationJobNotFoundException
import team.inreok.getiserver.domain.recommendation.exception.RecommendationNotEnrolledException
import team.inreok.getiserver.domain.recommendation.repository.MemberJobPreferenceRepository
import team.inreok.getiserver.domain.recommendation.repository.RecommendationGenerationStateRepository
import team.inreok.getiserver.domain.recommendation.repository.RecommendationPreferenceRepository
import team.inreok.getiserver.domain.recommendation.repository.RecommendationRepository
import team.inreok.getiserver.domain.recommendation.service.RecommendationService
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * [RecommendationService]의 구현이다(R3 Issue #152, Notion 계약 정합성 Issue #155, R4 Issue #160).
 * R2([team.inreok.getiserver.domain.recommendation.service.RecommendationGenerationService])가
 * 이미 계산·저장한 결과를 읽고 사용자 설정을 바꿀 뿐, Score/Hard Filter/Ranking을 다시 계산하지
 * 않는다. GENERATING/FAILED 상태는 R4 Daily Scheduler(`RecommendationGenerationRunner`)가 기록한
 * `RecommendationGenerationState`를 그대로 읽는다 -- 여기서도 상태를 직접 판정하지 않는다.
 */
@Service
class RecommendationServiceImpl(
    private val recommendationRepository: RecommendationRepository,
    private val recommendationPreferenceRepository: RecommendationPreferenceRepository,
    private val recommendationGenerationStateRepository: RecommendationGenerationStateRepository,
    private val memberJobPreferenceRepository: MemberJobPreferenceRepository,
    private val jobRecommendationCandidateQueryPort: JobRecommendationCandidateQueryPort,
    private val companyQuery: CompanyQuery,
    private val memberApplicantSnapshotQueryPort: MemberApplicantSnapshotQueryPort,
    private val objectMapper: ObjectMapper,
    // Recommendation R4(Issue #160)의 Daily Scheduler와 같은 property를 읽는다 -- Scheduler
    // 설정이 바뀌면 이 값도 자동으로 맞아야 하므로 별도로 하드코딩하지 않는다(요구사항 "Scheduler
    // 설정이 바뀌면 API 값도 자동으로 맞아야 한다").
    @param:Value("\${app.recommendation.generation-scheduler.cron:-}") private val generationSchedulerCron: String,
    // SIMILAR_JOBS 등록 즉시 반영(R6, Issue #167)에 쓴다. RecommendationGenerationServiceImpl과
    // 같은 property를 읽어 두 호출부가 서로 다른 Threshold를 쓰지 않게 한다.
    @param:Value("\${app.recommendation.similarity.tech-stack-threshold:0.6}") private val similarityThreshold: Double,
    private val aiAnalysisSearchQueryPort: AiAnalysisSearchQueryPort,
) : RecommendationService {
    private val log = LoggerFactory.getLogger(RecommendationServiceImpl::class.java)

    // GENERATING/FAILED 분기와 그 외(EMPTY/READY 재조회) 분기를 하나의 when으로 묶어 detekt
    // ReturnCount(허용 2개)를 넘기지 않는다 -- disabledResponse/inProgressResponse/nextGenerationAt
    // 자체는 상태를 갖지 않는 순수 조립 로직이라 RecommendationResponseMapper.kt의 최상위 함수로
    // 분리했다(detekt TooManyFunctions 허용 11개, `buildRecommendationJobResponse`와 같은 이유).
    @Transactional(readOnly = true)
    override fun getMyRecommendations(
        memberId: Long,
        suitabilityLevel: SuitabilityLevel?,
        pageable: Pageable,
    ): RecommendationListResponse {
        if (!isEnabled(memberId)) return disabledRecommendationListResponse(pageable)

        val state = recommendationGenerationStateRepository.findByMemberId(memberId)
        // GENERATING/FAILED인 회원은 오늘자 Recommendation Row가 아직 없거나(계산 시작 전) 이번
        // 실행에서 만들어지지 않았다(계산 실패, Transaction 자체가 Rollback됨) -- 실제 조회를 다시
        // 하지 않고 상태만으로 즉시 응답한다(Recommendation R4, Issue #160).
        return when (state?.status) {
            RecommendationGenerationStatus.GENERATING -> {
                inProgressRecommendationListResponse(
                    pageable,
                    RecommendationStatus.GENERATING,
                    nextGenerationAt(generationSchedulerCron),
                )
            }

            RecommendationGenerationStatus.FAILED -> {
                inProgressRecommendationListResponse(
                    pageable,
                    RecommendationStatus.FAILED,
                    nextGenerationAt(generationSchedulerCron),
                )
            }

            else -> {
                readyOrEmptyRecommendations(memberId, suitabilityLevel, pageable)
            }
        }
    }

    private fun readyOrEmptyRecommendations(
        memberId: Long,
        suitabilityLevel: SuitabilityLevel?,
        pageable: Pageable,
    ): RecommendationListResponse {
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
            nextGenerationAt = nextGenerationAt(generationSchedulerCron),
            page = toItemResponsePage(page, memberId),
        )
    }

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
        val saved = saveExclusionPreference(memberJobPreferenceRepository, preference, jobId, log)
        // 요구사항 "관심 없음 즉시 Recommendation 결과 처리" -- 조회는 항상 오늘자 결과만
        // 보여주므로 날짜 제한 없이 이 (memberId, jobId) 조합을 통째로 지워도 안전하고, 다음
        // 생성부터는 Hard Filter(NOT_INTERESTED)가 이 Job을 다시 걸러낸다.
        recommendationRepository.deleteAllByMemberIdAndJobId(memberId, jobId)
        // SIMILAR_JOBS는 다음 Scheduler를 기다리지 않고 오늘자 결과 중 유사 판정되는 Job도 즉시
        // 지운다(R6, Issue #167 §5) -- 새 Recommendation을 만들지 않고 기존 Row만 지운다.
        if (exclusionType == ExclusionType.SIMILAR_JOBS) {
            deleteSimilarJobRecommendations(
                recommendationRepository,
                aiAnalysisSearchQueryPort,
                memberId,
                jobId,
                similarityThreshold,
            )
        }

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

/**
 * SIMILAR_JOBS 등록 즉시 반영이다(R6, Issue #167 §5). 오늘자 Recommendation 중 기준(Source) Job과
 * 유사 판정되는 Job의 Row만 지운다 -- 새로 Recommendation을 만들지 않고 다른 회원/다른 Job에는
 * 영향이 없다. `RecommendationGenerationServiceImpl.generateForMember`와 같은
 * `RecommendationSimilarityEvaluator`(`isSimilarJob`/`techStackSetOf`)를 그대로 재사용해 두
 * 호출부가 서로 다른 Formula를 구현하지 않게 한다. Class 안에 두면 detekt `TooManyFunctions`
 * (허용 11개)를 넘어서 [saveExclusionPreference]와 같은 이유로 File 최상위 함수로 둔다.
 */
@Suppress("ReturnCount")
private fun deleteSimilarJobRecommendations(
    recommendationRepository: RecommendationRepository,
    aiAnalysisSearchQueryPort: AiAnalysisSearchQueryPort,
    memberId: Long,
    sourceJobId: Long,
    threshold: Double,
) {
    val existingJobIds =
        recommendationRepository.findJobIdsByMemberIdAndRecommendationDate(memberId, LocalDate.now()).toSet()
    if (existingJobIds.isEmpty()) return
    // 기준 Job의 AI 분석이 없거나 COMPLETED가 아니면 확장하지 않는다(보수적 Fallback, Issue #167
    // §4) -- Generation의 similarJobsSourceTechStackSets 계산과 동일한 근거다.
    val aiSnapshots = aiAnalysisSearchQueryPort.findCompletedByJobIds(existingJobIds + sourceJobId)
    val sourceTechStackIds = aiSnapshots[sourceJobId]?.let(::techStackSetOf) ?: return
    val similarJobIds =
        existingJobIds
            .filter { candidateJobId ->
                val candidateSnapshot = aiSnapshots[candidateJobId] ?: return@filter false
                isSimilarJob(sourceTechStackIds, techStackSetOf(candidateSnapshot), threshold)
            }.toSet()
    if (similarJobIds.isEmpty()) return
    recommendationRepository.deleteAllByMemberIdAndJobIdIn(memberId, similarJobIds)
}

/**
 * 관심 없음 등록(Notion `POST /api/v1/me/recommendation-exclusions`)의 최종 방어선이다(코드리뷰
 * 반영). `registerExclusion`의 `existsByMemberIdAndJobIdAndExclusionIsNotNull` 사전 확인과 이
 * 저장 사이에는 DB 잠금이 없어(TOCTOU), 같은 (memberId, jobId)에 대한 두 요청이 거의 동시에
 * 도착하면(더블 클릭·재시도) 둘 다 사전 확인을 통과할 수 있다.
 * `uk_member_job_preferences_member_job`(V24 Migration) UNIQUE 제약이 최종 방어선이다 -- 위반하면
 * DB가 `DataIntegrityViolationException`을 던지고 여기서 `EXCLUSION_ALREADY_EXISTS`(409)로
 * 변환한다(`JobApplicationServiceImpl.saveNewApplication()`과 동일한 패턴). Class 안에 두면
 * detekt `TooManyFunctions`(허용 11개)를 넘어서 File 최상위 함수로 둔다.
 */
private fun saveExclusionPreference(
    repository: MemberJobPreferenceRepository,
    preference: MemberJobPreference,
    jobId: Long,
    log: Logger,
): MemberJobPreference =
    try {
        repository.saveAndFlush(preference)
    } catch (ex: DataIntegrityViolationException) {
        // BusinessException은 cause를 받지 않아 원본 예외를 여기서 남긴다(detekt
        // SwallowedException 반영) -- 실제 제약 위반 여부를 나중에 DB 로그로도 추적할 수 있다.
        log.warn("관심 없음 동시 등록 요청이 DB 제약으로 차단됨(uk_member_job_preferences_member_job)", ex)
        throw RecommendationExclusionAlreadyExistsException(jobId)
    }
