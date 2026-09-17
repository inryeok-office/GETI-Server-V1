package team.inreok.getiserver.domain.program.service.impl

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.program.query.ProgramDiscordSendTargetQueryPort
import team.inreok.getiserver.domain.program.query.ProgramDiscordSendTargetSnapshot
import team.inreok.getiserver.domain.program.repository.ProgramRepository
import team.inreok.getiserver.domain.program.repository.ProgramTargetGradeRepository
import java.time.LocalDateTime

@Service
class ProgramDiscordSendTargetQueryPortImpl(
    private val programRepository: ProgramRepository,
    private val targetGradeRepository: ProgramTargetGradeRepository,
) : ProgramDiscordSendTargetQueryPort {
    @Transactional(readOnly = true)
    override fun countPublished(
        targetName: String?,
        targetGrade: Int?,
    ): Long = programRepository.countPublishedDiscordSendTargets(targetName, targetGrade)

    @Transactional(readOnly = true)
    override fun findPublished(
        targetName: String?,
        targetGrade: Int?,
        afterCreatedAt: LocalDateTime?,
        afterId: Long?,
        limit: Int,
    ): List<ProgramDiscordSendTargetSnapshot> {
        val programs =
            programRepository.findPublishedDiscordSendTargets(
                targetName,
                targetGrade,
                afterCreatedAt ?: FIRST_CURSOR_CREATED_AT,
                afterId ?: Long.MAX_VALUE,
                PageRequest.of(0, limit),
            )
        if (programs.isEmpty()) return emptyList()

        val programIds = programs.mapNotNull { it.id }
        val gradesByProgramId =
            targetGradeRepository
                .findAllByIdProgramIdIn(programIds)
                .groupBy { it.id.programId }
                .mapValues { (_, grades) -> grades.map { it.id.grade }.sorted() }

        return programs.map {
            val programId = requireNotNull(it.id) { "저장된 Program은 id를 가져야 합니다." }
            ProgramDiscordSendTargetSnapshot(
                programId = programId,
                title = it.title,
                targetGrades = gradesByProgramId[programId].orEmpty(),
                createdAt = requireNotNull(it.createdAt) { "저장된 Program은 createdAt을 가져야 합니다." },
            )
        }
    }

    private companion object {
        val FIRST_CURSOR_CREATED_AT = LocalDateTime.of(9999, 12, 31, 23, 59, 59)
    }
}
