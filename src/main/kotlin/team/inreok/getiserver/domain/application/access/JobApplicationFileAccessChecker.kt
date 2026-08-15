package team.inreok.getiserver.domain.application.access

import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.application.repository.JobApplicationRepository
import team.inreok.getiserver.domain.file.access.FileAccessChecker
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.member.query.InquiryAssigneeCandidateQueryPort

/**
 * 지원서 첨부파일의 다운로드 권한을 판정한다. `ownerId`는 파일이 연결된 지원서(`JobApplication`)의
 * ID다(Issue #134 요구사항 "권한 확인 후 다운로드"). 지원서 본인이거나, 교사·개발자면 허용한다 --
 * `JobApplicationAdminController`의 조회 권한("담당 공고 여부와 무관하게 모든 교사·개발자가 조회할
 * 수 있다", Issue #125)과 동일한 기준이다. 담당 교사로 좁히지 않는 이유는 상태 변경(승인·거절 등)
 * 권한과 조회 권한이 이미 분리되어 있고, 첨부파일 다운로드는 상태 변경이 아니라 조회에 속하기
 * 때문이다.
 *
 * 역할 판정에 [InquiryAssigneeCandidateQueryPort]를 재사용한다 -- "이 회원의 역할이 무엇인가"라는
 * 같은 조회이므로 거의 동일한 목적의 Named Interface를 또 만들지 않는다
 * ([InquiryFileAccessChecker][team.inreok.getiserver.domain.inquiry.access.InquiryFileAccessChecker]와
 * 동일한 판단).
 *
 * `FileAccessChecker`를 File 도메인이 소유하고 여기서 구현하는 이유는 Module 순환을 피하기
 * 위해서다(해당 Interface의 Class 주석 참고). 의존 방향은 `domain.application -> domain.file`이다.
 */
@Component
class JobApplicationFileAccessChecker(
    private val jobApplicationRepository: JobApplicationRepository,
    private val inquiryAssigneeCandidateQueryPort: InquiryAssigneeCandidateQueryPort,
) : FileAccessChecker {
    override val ownerType: FileOwnerType = FileOwnerType.JOB_APPLICATION

    override fun canDownload(
        requesterId: Long,
        ownerId: Long,
    ): Boolean {
        // 지원서가 없으면(삭제됐거나 존재하지 않으면) 접근을 거부한다 -- 없는 것과 같이 다룬다.
        val application = jobApplicationRepository.findById(ownerId).orElse(null) ?: return false
        return application.applicantMemberId == requesterId ||
            inquiryAssigneeCandidateQueryPort.findById(requesterId)?.roles.orEmpty().any {
                it == TEACHER_ROLE || it == DEVELOPER_ROLE
            }
    }

    private companion object {
        const val TEACHER_ROLE = "TEACHER"
        const val DEVELOPER_ROLE = "DEVELOPER"
    }
}
