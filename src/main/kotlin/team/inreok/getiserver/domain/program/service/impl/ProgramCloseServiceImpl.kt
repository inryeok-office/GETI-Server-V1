package team.inreok.getiserver.domain.program.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.program.entity.type.ProgramStatus
import team.inreok.getiserver.domain.program.repository.ProgramRepository
import team.inreok.getiserver.domain.program.service.ProgramCloseService
import java.time.LocalDateTime

/**
 * [ProgramCloseService]의 구현이다. `ProgramServiceImpl`의 다른 상태 전이(PUBLISHED/DELETED)와
 * 달리 `ProgramDiscordEvent`를 발행하지 않는다 -- Discord `PROGRAM_CLOSED` 연동은 이번 범위 밖의
 * 별도 이슈다(`ProgramDiscordAction`에 대응 값이 아직 없음,
 * `docs/notification/discord-event-wiring-plan.md` §2/§9 참고).
 */
@Service
class ProgramCloseServiceImpl(
    private val programRepository: ProgramRepository,
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
        return true
    }
}
