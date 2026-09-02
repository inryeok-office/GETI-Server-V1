package team.inreok.getiserver.domain.ai.event

import org.springframework.modulith.NamedInterface

/**
 * `job_ai_analyses`에 대한 분석 시도(최초 분석/수동 재분석)가 끝났음을 알리는 최소 계약이다
 * (Issue #144). `JobChangedEvent`와 같은 설계로 `jobId`만 담고 성공/실패 여부는 담지 않는다 --
 * 구독 측(`search`)이 이 Id로 [team.inreok.getiserver.domain.ai.query.AiAnalysisSearchQueryPort]를
 * 다시 조회해서 반영하면, Event가 중복되거나 순서가 뒤바뀌어 도착해도 항상 같은 최종 결과로
 * 수렴한다.
 *
 * 분석이 실패로 끝나도 발행한다 -- 성공했을 때만 발행하면, "COMPLETED였다가 재분석이 실패한"
 * 공고의 검색 결과가 실제로는 더 이상 유효하지 않은 이전 분석 값을 계속 보여주게 된다
 * (`AiAnalysisSearchQueryPort.findCompletedByJobIds`가 COMPLETED만 반환하므로, 재조회하면
 * 자연스럽게 AI 관련 색인 값이 비워진다).
 *
 * `AiAnalysisServiceImpl`이 `AiAnalysisTransitionService`(별도 Bean, 자체 `@Transactional`)의
 * `markCompleted`/`markFailed` 호출이 이미 Commit된 *뒤에* 발행하므로, `search`의 구독 측은
 * `AiAnalysisWorkEventListener`와 같은 이유로 `@TransactionalEventListener`가 아니라 일반
 * `@EventListener`를 써야 한다(발행 시점에 활성 Transaction이 없다).
 */
@NamedInterface
data class AiAnalysisCompletedEvent(
    val jobId: Long,
)
