package team.inreok.getiserver.domain.program.service.impl

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.program.entity.type.ProgramStatus
import team.inreok.getiserver.domain.program.event.ProgramDiscordAction
import team.inreok.getiserver.domain.program.event.ProgramDiscordEvent
import team.inreok.getiserver.domain.program.repository.ProgramRepository
import team.inreok.getiserver.domain.program.service.ProgramCloseService
import java.time.LocalDateTime

/**
 * [ProgramCloseService]의 구현이다. 실제로 CLOSED로 전이했을 때만 `ProgramServiceImpl`의 다른
 * 상태 전이(PUBLISHED/DELETED)와 같은 방식으로 [ProgramDiscordEvent]를 발행한다(Issue #120,
 * `docs/notification/discord-event-wiring-plan.md` §2/§9). Transaction Commit 이후에만
 * `ProgramDiscordEventListener`(`@TransactionalEventListener(AFTER_COMMIT)`)로 실제 전달돼
 * `PROGRAM_CLOSED` Template의 Discord Delivery를 예약한다 -- `JobServiceImpl`과 같은 방식이다.
 */
@Service
class ProgramCloseServiceImpl(
    private val programRepository: ProgramRepository,
    private val eventPublisher: ApplicationEventPublisher,
) : ProgramCloseService {
    // 여러 조건을 순서대로 확인하고 어긋나면 즉시 false를 반환하는 Guard Clause 방식이라 detekt
    // ReturnCount 기본 임계값(2)을 넘는다. ProgramEligibility.computeProgramEligibilityReason과
    // 동일한 방식으로 Suppress한다.
    @Suppress("ReturnCount")
    @Transactional
    override fun closeIfExpired(
        programId: Long,
        now: LocalDateTime,
    ): Boolean {
        val program = programRepository.findByIdForUpdate(programId) ?: return false
        if (program.status != ProgramStatus.PUBLISHED) return false
        val applicationEndedAt = program.applicationEndedAt ?: return false
        // ProgramEligibility.computeProgramEligibilityReason의 `now.isAfter(applicationEndedAt)`와
        // 동일한 경계다 -- applicationEndedAt 시각 그 자체는 아직 마감이 아니다.
        if (!now.isAfter(applicationEndedAt)) return false

        program.status = ProgramStatus.CLOSED
        programRepository.flush()
        eventPublisher.publishEvent(ProgramDiscordEvent(programId, ProgramDiscordAction.CLOSED))
        return true
    }
}
