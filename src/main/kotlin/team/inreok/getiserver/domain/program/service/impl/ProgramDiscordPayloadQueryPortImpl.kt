package team.inreok.getiserver.domain.program.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.program.query.ProgramDiscordPayloadQueryPort
import team.inreok.getiserver.domain.program.query.ProgramDiscordPayloadSnapshot
import team.inreok.getiserver.domain.program.repository.ProgramRepository
import team.inreok.getiserver.domain.program.repository.ProgramTargetGradeRepository

/**
 * 다른 Domain Module(notification)에 공개된 조회 계약([ProgramDiscordPayloadQueryPort])의
 * 구현이다. [ProgramNotificationTargetQueryPortImpl]과 같은 이유로 `ProgramServiceImpl`에
 * 합치지 않고 분리한다.
 */
@Service
class ProgramDiscordPayloadQueryPortImpl(
    private val programRepository: ProgramRepository,
    private val programTargetGradeRepository: ProgramTargetGradeRepository,
) : ProgramDiscordPayloadQueryPort {
    @Transactional(readOnly = true)
    override fun findById(programId: Long): ProgramDiscordPayloadSnapshot? {
        // 삭제된 프로그램도 DELETE_NOTICE의 대상이므로 Soft Delete를 거르지 않는다.
        val program = programRepository.findById(programId).orElse(null) ?: return null
        return ProgramDiscordPayloadSnapshot(
            programId = requireNotNull(program.id) { "저장된 Program은 id를 가져야 합니다." },
            title = program.title,
            bodyMarkdown = program.bodyMarkdown,
            eventStartedAt = program.eventStartedAt,
            eventEndedAt = program.eventEndedAt,
            discordChannelId = program.discordChannelId,
            targetGrades = programTargetGradeRepository.findAllByIdProgramId(programId).map { it.id.grade },
            updatedAt = requireNotNull(program.updatedAt) { "저장된 Program은 updatedAt을 가져야 합니다." },
        )
    }

    @Transactional(readOnly = true)
    override fun findDisplayNamesByIds(programIds: Set<Long>): Map<Long, String> {
        if (programIds.isEmpty()) return emptyMap()
        // findById와 같은 이유로 Soft Delete를 거르지 않는다(Port KDoc 참고).
        return programRepository
            .findAllById(programIds)
            .associate { requireNotNull(it.id) { "저장된 Program은 id를 가져야 합니다." } to it.title }
    }
}
