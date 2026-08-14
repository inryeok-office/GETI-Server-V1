package team.inreok.getiserver.domain.program.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.program.entity.type.ProgramApplicationStatus
import team.inreok.getiserver.domain.program.query.ProgramApplicantQueryPort
import team.inreok.getiserver.domain.program.repository.ProgramApplicationRepository

/**
 * 다른 Domain Module(notification)에 공개된 조회 계약([ProgramApplicantQueryPort])의 구현이다.
 * [ProgramNotificationTargetQueryPortImpl]과 같은 이유로 `ProgramServiceImpl`에 합치지 않고
 * 분리한다.
 */
@Service
class ProgramApplicantQueryPortImpl(
    private val programApplicationRepository: ProgramApplicationRepository,
) : ProgramApplicantQueryPort {
    @Transactional(readOnly = true)
    override fun findActiveApplicantMemberIds(programId: Long): List<Long> =
        programApplicationRepository.findApplicantMemberIdsByProgramIdAndStatus(
            programId,
            ProgramApplicationStatus.APPLIED,
        )
}
