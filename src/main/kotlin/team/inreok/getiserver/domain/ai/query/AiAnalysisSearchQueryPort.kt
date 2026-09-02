package team.inreok.getiserver.domain.ai.query

import org.springframework.modulith.NamedInterface

/**
 * `search`가 Elasticsearch Document에 AI 분석 결과를 반영하기 위해 읽는 공개 계약이다(Issue #144).
 * `search`는 이 Interface를 통해서만 AI 분석 결과를 읽고, `JobAiAnalysis` Entity나
 * `JobAiAnalysisRepository`를 직접 참조하지 않는다. `search`가 `ai.query`를 참조하는 방향은
 * `job.query`/`company.query`를 참조하는 기존 관례와 같다 -- `ai`는 `search`의 어떤 Package도
 * 참조하지 않으므로 순환 의존이 생기지 않는다.
 *
 * `findCompletedByJobIds`만 두고 단건 조회 Method를 따로 두지 않는다 -- 증분 동기화(공고 1건)도
 * 이 Method를 크기 1인 Collection으로 호출해서 재사용하고, 전체 Reindex는 Batch로 호출해 Job
 * 건수만큼 반복 조회(N+1)하지 않는다(`TechStackMatchQueryPort.matchByNames`와 같은 Batch Map 반환
 * 방식).
 */
@NamedInterface
interface AiAnalysisSearchQueryPort {
    /**
     * `status`가 COMPLETED인 분석만 반환한다 -- 결과 Map에 없는 `jobId`는 "확정된 AI 분석 결과가
     * 없음"(아직 분석되지 않았거나, 진행 중이거나, 마지막 시도가 실패함)으로 취급한다.
     *
     * PROCESSING/FAILED에서도 이전에 COMPLETED였을 때 채워진 값(`summary`/`requiredSkills` 등)이
     * Entity에 그대로 남아 있을 수 있다(`AiAnalysisTransitionServiceImpl.markFailed`는 이 값을
     * 지우지 않는다). 재분석이 실패했는데도 검색 결과가 마치 최신 분석이 성공한 것처럼 이전 값을
     * 계속 노출하면 안 되므로, 이 계약은 의도적으로 COMPLETED만 반환한다(Issue #144 설계 결정).
     */
    fun findCompletedByJobIds(jobIds: Collection<Long>): Map<Long, AiAnalysisSearchSnapshot>
}

/**
 * Search 색인에 노출해도 안전한 값만 담는다. `provider`/`model`/`promptVersion`/`errorMessage`/
 * `requestedAt`이나 OpenAI 원본 응답 같은 내부 세부값은 담지 않는다(Issue #144 요구사항).
 */
@NamedInterface
data class AiAnalysisSearchSnapshot(
    /** GETI Tech Stack Master와 매칭된 필수 기술 ID만 담는다. 매칭되지 않은 기술 이름은 포함하지 않는다. */
    val requiredTechStackIds: List<Long>,
    /** GETI Tech Stack Master와 매칭된 우대 기술 ID만 담는다. 매칭되지 않은 기술 이름은 포함하지 않는다. */
    val preferredTechStackIds: List<Long>,
    /** `AiFitLevel.name` */
    val highSchoolGraduateFit: String?,
    /** `AiFitLevel.name` */
    val entryLevelFit: String?,
    /** `AiDifficulty.name` */
    val difficulty: String?,
)
