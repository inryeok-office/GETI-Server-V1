package team.inreok.getiserver.domain.job.access

import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.file.access.FileAccessChecker
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.job.repository.JobRepository
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.query.MemberRoleQueryPort

/**
 * 공고 첨부파일(`FileOwnerType.JOB`)의 다운로드 권한을 판정한다(Issue #126). `ownerId`는 파일이
 * 연결된 공고의 ID다. 실제 판정 규칙은 [canViewJobFiles]에 있다 -- 상세 응답의 첨부파일 목록
 * 노출 여부와 같은 규칙을 써야 한다(해당 함수 KDoc 참고).
 *
 * `FileAccessChecker`를 File 도메인이 소유하고 여기서 구현하는 이유는 Module 순환을 피하기
 * 위해서다(`InquiryFileAccessChecker`/`ProgramFileAccessChecker`와 동일한 이유). 의존 방향은
 * `domain.job -> domain.file`, `domain.job -> domain.member`다.
 */
@Component
class JobFileAccessChecker(
    private val jobRepository: JobRepository,
    private val memberRoleQueryPort: MemberRoleQueryPort,
) : FileAccessChecker {
    override val ownerType: FileOwnerType = FileOwnerType.JOB

    override fun canDownload(
        requesterId: Long,
        ownerId: Long,
    ): Boolean {
        // 삭제됐거나 존재하지 않는 공고는 접근을 거부한다 -- 없는 것과 같이 다룬다
        // (InquiryFileAccessChecker/ProgramFileAccessChecker와 동일한 원칙).
        val job = jobRepository.findByIdAndDeletedAtIsNull(ownerId) ?: return false
        return canViewJobFiles(job, requesterId) {
            RoleType.DEVELOPER in memberRoleQueryPort.findRoles(requesterId)
        }
    }
}
