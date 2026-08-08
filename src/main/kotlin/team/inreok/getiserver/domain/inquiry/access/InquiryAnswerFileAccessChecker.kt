package team.inreok.getiserver.domain.inquiry.access

import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.file.access.FileAccessChecker
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.inquiry.repository.InquiryAnswerRepository
import team.inreok.getiserver.domain.inquiry.repository.InquiryRepository
import team.inreok.getiserver.domain.member.query.InquiryAssigneeCandidateQueryPort

/**
 * 문의 답변 첨부파일의 다운로드 권한을 판정한다. `ownerId`는 파일이 연결된 [InquiryAnswer]의
 * ID다(요구사항 §38/§45). 답변 첨부는 개발자가 등록하고 학생(문의 작성자)이 내려받는 방향이라,
 * [InquiryFileAccessChecker](문의 본문 첨부)와 검증 대상이 다르다 -- 여기서는 "이 답변의
 * 작성자"가 아니라 "이 답변이 달린 문의의 작성자"인지를 본다.
 *
 * 허용 대상: 해당 답변이 속한 문의의 작성자 본인, 또는 개발자(문의 관리 권한). 개발자 여부 판정에
 * [InquiryAssigneeCandidateQueryPort]를 재사용하는 이유는 [InquiryFileAccessChecker]와 같다.
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
