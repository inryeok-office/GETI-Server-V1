package team.inreok.getiserver.domain.program.access

import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.file.access.FileAccessChecker
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.query.MemberRoleQueryPort
import team.inreok.getiserver.domain.program.repository.ProgramRepository

/**
 * Program 본문 첨부파일(`FileOwnerType.PROGRAM`)의 다운로드 권한을 판정한다(Issue #127). `ownerId`는
 * 파일이 연결된 Program의 ID다. 실제 판정 규칙은 [canViewProgramFiles]에 있다 -- 상세 응답의
 * 첨부파일 목록 노출 여부와 같은 규칙을 써야 한다(KDoc 참고).
 *
 * `FileAccessChecker`를 File 도메인이 소유하고 여기서 구현하는 이유는 Module 순환을 피하기
 * 위해서다(`InquiryFileAccessChecker`와 동일한 이유). 의존 방향은 `domain.program -> domain.file`,
 * `domain.program -> domain.member`다.
 */
@Component
class ProgramFileAccessChecker(
    private val programRepository: ProgramRepository,
    private val memberRoleQueryPort: MemberRoleQueryPort,
) : FileAccessChecker {
    override val ownerType: FileOwnerType = FileOwnerType.PROGRAM

    override fun canDownload(
        requesterId: Long,
        ownerId: Long,
    ): Boolean {
        // 삭제됐거나 존재하지 않는 Program은 접근을 거부한다 -- 없는 것과 같이 다룬다
        // (InquiryFileAccessChecker와 동일한 원칙).
        val program = programRepository.findByIdAndDeletedAtIsNull(ownerId) ?: return false
        val isDeveloper = RoleType.DEVELOPER in memberRoleQueryPort.findRoles(requesterId)
        return canViewProgramFiles(program, requesterId, isDeveloper)
    }
}
