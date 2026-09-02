package team.inreok.getiserver.domain.portfolio.access

import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.file.access.FileAccessChecker
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.member.query.InquiryAssigneeCandidateQueryPort
import team.inreok.getiserver.domain.portfolio.repository.PortfolioSubmissionRepository

/**
 * 포트폴리오 제출물 첨부파일의 다운로드 권한을 판정한다(요구사항 §17, Issue #286 Phase 3). `ownerId`는
 * 파일이 연결된 제출물(`PortfolioSubmission`)의 ID다. 제출 학생 본인이거나, 교사·개발자면 허용한다 --
 * 관리자 제출 현황(Issue #204 Phase 2b)에서 학생별 자료를 개별 확인·다운로드하는 흐름과 같은 기준이다
 * (담당 여부와 무관하게 모든 교사·개발자 허용, `JobApplicationFileAccessChecker`와 동일한 판단).
 *
 * 업로더 본인은 `FileAccessResolver`가 이미 항상 허용하므로 여기서 다시 다루지 않지만, "제출 학생
 * 본인"을 명시적으로 허용해 재제출로 파일 소유자와 제출자가 갈릴 여지가 있어도 제출자 관점의 접근이
 * 유지되게 한다.
 *
 * 역할 판정에 [InquiryAssigneeCandidateQueryPort]를 재사용한다 -- "이 회원의 역할이 무엇인가"라는 같은
 * 조회이므로 거의 동일한 목적의 Named Interface를 또 만들지 않는다
 * ([JobApplicationFileAccessChecker][team.inreok.getiserver.domain.application.access.JobApplicationFileAccessChecker]와
 * 동일한 판단).
 *
 * `FileAccessChecker`를 File 도메인이 소유하고 여기서 구현하는 이유는 Module 순환을 피하기 위해서다
 * (해당 Interface의 Class 주석 참고). 의존 방향은 `domain.portfolio -> domain.file`이다.
 */
@Component
class PortfolioSubmissionFileAccessChecker(
    private val submissionRepository: PortfolioSubmissionRepository,
    private val inquiryAssigneeCandidateQueryPort: InquiryAssigneeCandidateQueryPort,
) : FileAccessChecker {
    override val ownerType: FileOwnerType = FileOwnerType.PORTFOLIO_SUBMISSION

    override fun canDownload(
        requesterId: Long,
        ownerId: Long,
    ): Boolean {
        // 제출물이 없으면(삭제됐거나 존재하지 않으면) 접근을 거부한다 -- 없는 것과 같이 다룬다.
        val submission = submissionRepository.findById(ownerId).orElse(null) ?: return false
        return submission.memberId == requesterId ||
            inquiryAssigneeCandidateQueryPort.findById(requesterId)?.roles.orEmpty().any {
                it == TEACHER_ROLE || it == DEVELOPER_ROLE
            }
    }

    private companion object {
        const val TEACHER_ROLE = "TEACHER"
        const val DEVELOPER_ROLE = "DEVELOPER"
    }
}
