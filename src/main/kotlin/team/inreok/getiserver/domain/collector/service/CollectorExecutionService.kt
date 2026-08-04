package team.inreok.getiserver.domain.collector.service

interface CollectorExecutionService {
    /**
     * 활성화(enabled)되고 승인(READY)된 모든 수집원에 대해 등록된 Provider를 순서대로 실행한다.
     * Provider 하나가 실패해도 다른 Provider 실행은 계속된다(Scheduler와 향후 수동 실행 API가
     * 공유하는 진입점).
     */
    fun runDailyCollection()
}
