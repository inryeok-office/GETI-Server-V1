package team.inreok.getiserver.domain.collector.service

import team.inreok.getiserver.domain.collector.entity.CollectionRun
import team.inreok.getiserver.domain.collector.entity.type.CollectorAction

interface CollectorExecutionService {
    /**
     * 활성화(enabled)되고 승인(READY)된 모든 수집원에 대해 등록된 Provider를 순서대로 실행한다.
     * Provider 하나가 실패해도 다른 Provider 실행은 계속된다. 이미 진행 중인 실행이나 등록된
     * Provider가 없는 Source는 조용히 Skip한다(Scheduler 전용 진입점, 실행이 동기로 끝난다).
     */
    fun runDailyCollection()

    /**
     * `POST /api/v1/admin/collector-actions`가 사용하는 수동 실행 진입점이다. 모든 `sourceId`를
     * 먼저 검증해(승인·설정·중복 실행 여부) 하나라도 실패하면 아무 실행도 만들지 않고 즉시
     * 예외를 던진다. 검증을 통과하면 각 Source의 [CollectionRun]을 PENDING으로 즉시 생성해
     * 반환하고, 실제 Provider 호출은 별도 Thread에서 비동기로 진행한다(호출 Thread는 외부 수집
     * 완료를 기다리지 않고 즉시 반환, 202 Accepted).
     */
    fun triggerManual(
        action: CollectorAction,
        sourceIds: List<Long>,
    ): List<CollectionRun>
}
