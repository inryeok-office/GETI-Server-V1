package team.inreok.getiserver.domain.program.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.program.query.ProgramManagerQueryPort
import team.inreok.getiserver.domain.program.query.ProgramManagerSnapshot
import team.inreok.getiserver.domain.program.repository.ProgramRepository

/**
 * 다른 Domain Module(notification)에 공개된 조회 계약([ProgramManagerQueryPort])의 구현이다.
 * [ProgramDiscordPayloadQueryPortImpl]과 같은 이유로 `ProgramServiceImpl`에 합치지 않고 분리한다.
 */
@Service
class ProgramManagerQueryPortImpl(
    private val programRepository: ProgramRepository,
) : ProgramManagerQueryPort {
    @Transactional(readOnly = true)
    override fun findById(programId: Long): ProgramManagerSnapshot? {
        val program = programRepository.findById(programId).orElse(null) ?: return null
        return ProgramManagerSnapshot(
            programId = requireNotNull(program.id) { "저장된 Program은 id를 가져야 합니다." },
            createdByMemberId = program.createdByMemberId,
            managerMemberId = program.managerMemberId,
        )
    }
}
