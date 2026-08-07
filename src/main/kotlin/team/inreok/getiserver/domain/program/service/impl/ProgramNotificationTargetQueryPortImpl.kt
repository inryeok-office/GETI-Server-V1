package team.inreok.getiserver.domain.program.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.program.query.ProgramNotificationTargetQueryPort
import team.inreok.getiserver.domain.program.query.ProgramNotificationTargetSnapshot
import team.inreok.getiserver.domain.program.repository.ProgramRepository

/**
 * 다른 Domain Module(notification)에 공개된 조회 계약([ProgramNotificationTargetQueryPort])의
 * 구현이다. `JobNotificationTargetQueryPortImpl`과 같은 이유로 `ProgramServiceImpl`에 합치지
 * 않고 분리한다.
 */
@Service
class ProgramNotificationTargetQueryPortImpl(
    private val programRepository: ProgramRepository,
) : ProgramNotificationTargetQueryPort {
    @Transactional(readOnly = true)
    override fun findAllByIds(programIds: Set<Long>): Map<Long, ProgramNotificationTargetSnapshot> {
        if (programIds.isEmpty()) return emptyMap()
        // 삭제된 프로그램도 "삭제됨"으로 판정해야 하므로 findByIdAndDeletedAtIsNull이 아니라
        // Soft Delete를 거르지 않는 findAllById를 쓴다.
        return programRepository
            .findAllById(programIds)
            .mapNotNull { program ->
                val programId = program.id ?: return@mapNotNull null
                programId to
                    ProgramNotificationTargetSnapshot(
                        programId = programId,
                        status = program.status.name,
                        deleted = program.deletedAt != null,
                    )
            }.toMap()
    }
}
