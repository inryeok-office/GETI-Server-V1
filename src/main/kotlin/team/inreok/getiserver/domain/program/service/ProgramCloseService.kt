package team.inreok.getiserver.domain.program.service

import java.time.LocalDateTime

/**
 * 신청 종료 시각이 지난 PUBLISHED Program을 CLOSED로 전이한다(원본 요구사항 문서 8절, Phase 7).
 * `ProgramService.changeStatus()`가 참조하는 `allowedTransitions()`는 여전히 PUBLISHED -> CLOSED를
 * 허용하지 않아 이 전이는 API로 지정할 수 없다 -- 이 Interface는 `ProgramCloseScheduler` 전용으로
 * 분리한 내부 전이 경로이며, 어떤 Controller도 이 Interface에 의존하지 않는다.
 */
interface ProgramCloseService {
    /**
     * `programId`를 잠근 뒤(`findByIdForUpdate`) 그 시점에도 PUBLISHED이고 `applicationEndedAt`이
     * `now`보다 앞서 있으면 CLOSED로 전이한다. 이미 다른 경로로 상태가 바뀌었거나 조건을 더는
     * 만족하지 않으면 아무것도 하지 않는다(멱등) -- 대상 목록 조회와 실제 처리 사이의 TOCTOU, 여러
     * Scheduler 실행이 겹치는 상황 모두 이 재확인으로 안전하다.
     *
     * @return 실제로 CLOSED로 전이했으면 true, 아무 것도 하지 않았으면 false
     */
    fun closeIfExpired(
        programId: Long,
        now: LocalDateTime,
    ): Boolean
}
