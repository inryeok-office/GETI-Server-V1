package team.inreok.getiserver.domain.ai.service

import team.inreok.getiserver.domain.ai.dto.AiReanalysisResponse

interface AiAnalysisService {
    /**
     * Job이 처음 PUBLISHED된 뒤 호출된다(`JobPublishedAiTriggerListener`). 이미 분석 Row가
     * 있으면 아무 것도 하지 않는다 -- PUBLISHED Job의 이후 수정으로 인한 자동 재분석은 Phase 1
     * 범위가 아니다(GETI Notion AI Analysis Phase 1 "Job 수정 후 재분석" 절, Follow-up).
     */
    fun triggerInitialAnalysisIfAbsent(jobId: Long)

    /** 사용자가 명시적으로 요청한 수동 재분석이다. 최대 [MAX_REANALYSIS_COUNT]회로 제한한다. */
    fun reanalyze(jobId: Long): AiReanalysisResponse

    /**
     * 실제 OpenAI 호출을 수행하는 비동기 Worker 본체다. PENDING이 아니면 아무 것도 하지 않고
     * 반환한다(중복 처리 방지). `AiAnalysisRequestedEvent` Listener가 `aiTaskExecutor`에서 호출한다.
     */
    fun processAnalysis(jobId: Long)
}
