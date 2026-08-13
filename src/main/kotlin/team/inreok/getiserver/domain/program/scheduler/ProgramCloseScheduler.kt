package team.inreok.getiserver.domain.program.scheduler

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.program.entity.type.ProgramStatus
import team.inreok.getiserver.domain.program.repository.ProgramRepository
import team.inreok.getiserver.domain.program.service.ProgramCloseService
import java.time.LocalDateTime

/**
 * 신청 종료 시각이 지난 PUBLISHED Program을 CLOSED로 자동 전이한다(원본 요구사항 문서 8절, Phase
 * 7). `SearchIndexRetryScheduler`와 같은 구조다 -- 이 Class는 대상 Id를 조회해 순회만 하고, 실제
 * 전이는 `ProgramCloseService`에 위임한다("언제 도는가"만 알고 "무엇을 하는가"는 모른다).
 *
 * 서버가 한 대만 배포된 현재 전제에서는 여러 Instance 동시 실행을 막는 분산 Lock(ShedLock 등)이
 * 없다(`CollectorScheduler`와 동일한 근거). `ProgramCloseService.closeIfExpired`가 Row Lock 후
 * 상태를 다시 확인해 멱등하게 동작하므로, 중복 실행 자체가 문제가 되지는 않는다.
 *
 * 한 Program 처리 실패가 나머지 대상 처리를 막지 않도록 `runCatching`으로 건별 격리한다
 * (`SearchIndexRetryScheduler.retryDueFailures`와 동일한 방식).
 */
@Component
class ProgramCloseScheduler(
    private val programRepository: ProgramRepository,
    private val programCloseService: ProgramCloseService,
) {
    private val log = LoggerFactory.getLogger(ProgramCloseScheduler::class.java)

    @Scheduled(fixedDelayString = "\${app.program.close-scheduler.interval-ms:60000}")
    fun closeExpiredPrograms() {
        val now = LocalDateTime.now()
        val programIds = programRepository.findExpiredPublishedIds(ProgramStatus.PUBLISHED, now)
        programIds.forEach { programId ->
            runCatching { programCloseService.closeIfExpired(programId, now) }
                .onFailure { ex -> log.error("프로그램 자동 마감 처리 중 처리되지 않은 오류(programId={})", programId, ex) }
        }
    }
}
