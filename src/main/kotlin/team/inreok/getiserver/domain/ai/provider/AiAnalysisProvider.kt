package team.inreok.getiserver.domain.ai.provider

import team.inreok.getiserver.domain.ai.entity.type.AiDifficulty
import team.inreok.getiserver.domain.ai.entity.type.AiFitLevel
import java.time.LocalDateTime

/**
 * 공고 AI 분석을 실제로 수행하는 외부 Provider의 SPI다(GETI Notion AI Analysis Phase 1,
 * `docs/collector/`의 `CollectorProvider`와 같은 목적의 Port/Adapter 분리). `AiAnalysisServiceImpl`은
 * 이 Interface에만 의존하고 OpenAI HTTP 호출 세부사항(Endpoint, Request/Response 형식)을 알지
 * 못한다 — 향후 다른 Provider가 추가돼도 Use Case는 바뀌지 않는다(Phase 1은 OpenAI 단일 구현만
 * 둔다, Multi Provider Failover는 이번 범위가 아니다).
 */
interface AiAnalysisProvider {
    /** 이 Provider가 실제로 호출할 준비(API Key 등)가 되어 있는지. Secret 원문은 노출하지 않는다. */
    fun isConfigured(): Boolean

    /**
     * 공고 하나를 분석한다. Provider가 스스로 복구할 수 없는 오류는
     * [AiAnalysisProviderException]의 하위 타입으로 던진다. 반환값은 이미 Structured Output
     * Schema를 통과한 값이므로 호출 측이 다시 자유 형식 Text를 Parsing할 필요가 없다.
     */
    fun analyze(input: AiAnalysisInput): AiAnalysisOutput
}

/**
 * Provider에 전달하는 최소 입력이다. `job` Module의 [team.inreok.getiserver.domain.job.query.JobAiAnalysisInputQueryPort]가
 * 채우며, 실제 Job/Company에 존재하는 값만 담는다(내부 ID, 개인정보, Token 등은 포함하지 않는다).
 */
data class AiAnalysisInput(
    val jobId: Long,
    val title: String,
    /** `PostingType.name` */
    val postingType: String,
    /** `ApplicationMethod.name` */
    val applicationMethod: String,
    val companyName: String?,
    val content: String?,
    val startDate: LocalDateTime?,
    val endDate: LocalDateTime?,
    val targetGrade: Int?,
)

/** Provider가 실제로 호출한 Model 이름을 포함한다 — Business Code에 Model 이름을 Hard Coding하지 않기 위함이다. */
data class AiAnalysisOutput(
    val summary: String,
    val requiredSkillNames: List<String>,
    val preferredSkillNames: List<String>,
    val highSchoolGraduateFit: AiFitLevel,
    val entryLevelFit: AiFitLevel,
    val difficulty: AiDifficulty,
    val model: String,
)
