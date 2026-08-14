package team.inreok.getiserver.domain.ai.service

import team.inreok.getiserver.domain.ai.entity.type.AiStatus
import team.inreok.getiserver.domain.ai.provider.AiAnalysisOutput
import java.time.LocalDateTime

/**
 * `job_ai_analyses`의 상태 전이를 원자적으로 수행하는 계약이다. `AiAnalysisServiceImpl`이 이
 * Interface에 위임하는 이유는 Spring Self-Invocation 문제 때문이다 -- 같은 Bean 안에서
 * `@Transactional` Method를 직접 호출하면 Proxy를 거치지 않아 Transaction이 시작되지 않는다.
 * 모든 Method가 `JobAiAnalysisRepository.findByIdForUpdate`(Pessimistic Lock)로 동시 접근을
 * 막으므로, Web 요청 Thread(수동 재분석)와 `aiTaskExecutor` Thread(비동기 Worker)가 겹쳐도
 * 같은 Job을 중복으로 OpenAI에 보내지 않는다(GETI Notion AI Analysis Phase 1 "중복 처리 방지").
 */
interface AiAnalysisTransitionService {
    /** 이미 Row가 있으면 아무 것도 하지 않고 false. 없으면 PENDING Row를 만들고 true. */
    fun createPendingIfAbsent(jobId: Long): Boolean

    /**
     * 수동 재분석을 접수한다. PROCESSING이면 [team.inreok.getiserver.domain.ai.exception.AiAlreadyProcessingException],
     * 3회를 모두 썼으면 [team.inreok.getiserver.domain.ai.exception.AiReanalysisLimitExceededException]을 던진다.
     */
    fun prepareReanalysis(jobId: Long): AiReanalysisPreparation

    /** PENDING -> PROCESSING. 이미 PENDING이 아니면(중복 Event 등) false. */
    fun claimForProcessing(jobId: Long): Boolean

    fun markCompleted(
        jobId: Long,
        output: AiAnalysisOutput,
        requiredSkillsJson: String,
        preferredSkillsJson: String,
        promptVersion: String,
        analysisVersion: Int,
    )

    /** [sanitizedMessage]는 이미 Secret/원본 예외 Stack Trace가 제거된 안전한 문구여야 한다. */
    fun markFailed(
        jobId: Long,
        sanitizedMessage: String,
    )
}

/** [prepareReanalysis]의 결과. `AiAnalysisServiceImpl`이 `jobId`를 더해 `AiReanalysisResponse`로 감싼다. */
data class AiReanalysisPreparation(
    val status: AiStatus,
    val isReanalysis: Boolean,
    val reanalysisCount: Int,
    val remainingReanalysisCount: Int,
    val canReanalyze: Boolean,
    val requestedAt: LocalDateTime,
)

const val MAX_REANALYSIS_COUNT = 3

/** 남은 수동 재분석 가능 횟수. 음수가 되지 않도록 하한을 둔다. */
fun remainingReanalysisCount(reanalysisCount: Int): Int = (MAX_REANALYSIS_COUNT - reanalysisCount).coerceAtLeast(0)
