package team.inreok.getiserver.domain.recommendation.service.impl

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import team.inreok.getiserver.domain.recommendation.service.RecommendationGenerationRunner
import team.inreok.getiserver.domain.recommendation.service.RecommendationGenerationService
import team.inreok.getiserver.domain.recommendation.service.RecommendationGenerationStateService

/**
 * [RecommendationGenerationRunner]의 구현이다(Recommendation R4, Issue #160).
 *
 * 이 Class는 `@Transactional`을 붙이지 않는다 -- `markGenerating`/`generateForMember`(R2
 * Core)/`markCompleted`(또는 `markFailed`)가 각각 자신의 Transaction으로 즉시 Commit되어야
 * GENERATING 상태가 계산이 끝나기 전에도 조회 가능하고, 회원 1명 처리가 세 개의 작은 Transaction으로
 * 나뉘어 큰 Transaction 하나로 묶이지 않는다(요구사항 "회원별 Transaction 분리, 거대 Transaction
 * 금지").
 *
 * Top N(`app.recommendation.generation-scheduler.top-n`)은 확정 근거가 없어(DECISION_REQUIRED, PR
 * 본문 참고) Configuration으로 분리했다.
 */
@Service
class RecommendationGenerationRunnerImpl(
    private val generationStateService: RecommendationGenerationStateService,
    private val recommendationGenerationService: RecommendationGenerationService,
    @param:Value("\${app.recommendation.generation-scheduler.top-n:20}") private val topN: Int,
) : RecommendationGenerationRunner {
    // 한 회원의 Generation 실패가 Scheduler 전체를 막지 않도록 여기서 넓게 잡아 상태만 FAILED로
    // 기록하고 다시 던진다(CollectorExecutionServiceImpl.upsertJob과 동일한 근거).
    @Suppress("TooGenericExceptionCaught")
    override fun generateForMember(memberId: Long) {
        generationStateService.markGenerating(memberId)
        val result =
            try {
                recommendationGenerationService.generateForMember(memberId, topN)
            } catch (ex: Exception) {
                generationStateService.markFailed(memberId)
                throw ex
            }
        generationStateService.markCompleted(memberId, hasResults = result.isNotEmpty())
    }
}
