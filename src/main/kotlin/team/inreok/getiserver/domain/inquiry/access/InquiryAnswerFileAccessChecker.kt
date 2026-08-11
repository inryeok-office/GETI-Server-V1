package team.inreok.getiserver.domain.inquiry.access

import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.file.access.FileAccessChecker
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.inquiry.repository.InquiryAnswerRepository
import team.inreok.getiserver.domain.inquiry.repository.InquiryRepository
import team.inreok.getiserver.domain.member.query.InquiryAssigneeCandidateQueryPort

/**
 * 문의 답변 첨부파일의 다운로드 권한을 판정한다. `ownerId`는 파일이 연결된 [InquiryAnswer]의
 * ID다(요구사항 §38/§45). [InquiryFileAccessChecker](문의 본문 첨부)와 검증 대상이 다르다 --
 * 여기서는 "이 답변의 작성자"가 아니라 "이 답변이 달린 문의의 작성자"인지를 본다.
 *
 * 허용 대상은 다음 두 그룹이며 우선순위가 없다(둘 중 하나만 만족하면 허용):
 * 1. 해당 답변이 속한 문의의 작성자 본인.
 * 2. `role=DEVELOPER`인 회원 **전원** -- 그 문의의 담당자(assignee)로 지정된 개발자만이 아니라,
 *    개발자 Role을 가진 모든 회원이 대상이다. 원본 요구사항 문서 §44가 "DEVELOPER: Inquiry 관리
 *    권한 정책상 해당 File 조회 가능"이라고 담당자 한정 없이 명시했기 때문이다(사용자 확인 완료).
 *    개발자 여부 판정에 [InquiryAssigneeCandidateQueryPort]를 재사용하는 이유는
 *    [InquiryFileAccessChecker]와 같다 -- "이 회원의 역할이 무엇인가"라는 같은 조회를 위해 거의
 *    동일한 목적의 Named Interface를 또 만들지 않았다.
 */
@Component
class InquiryAnswerFileAccessChecker(
    private val inquiryAnswerRepository: InquiryAnswerRepository,
    private val inquiryRepository: InquiryRepository,
    private val inquiryAssigneeCandidateQueryPort: InquiryAssigneeCandidateQueryPort,
) : FileAccessChecker {
    override val ownerType: FileOwnerType = FileOwnerType.INQUIRY_ANSWER

    override fun canDownload(
        requesterId: Long,
        ownerId: Long,
    ): Boolean {
        // 답변이나 그 답변이 속한 문의가 없으면(삭제됐거나 존재하지 않으면) 접근을 거부한다 --
        // 없는 것과 같이 다룬다.
        val answer = inquiryAnswerRepository.findById(ownerId).orElse(null) ?: return false
        val inquiry = inquiryRepository.findById(answer.inquiryId).orElse(null)
        return inquiry != null &&
            (
                inquiry.authorMemberId == requesterId ||
                    inquiryAssigneeCandidateQueryPort.findById(requesterId)?.roles?.contains(DEVELOPER_ROLE) == true
            )
    }

    private companion object {
        const val DEVELOPER_ROLE = "DEVELOPER"
    }
}
