package team.inreok.getiserver.domain.inquiry.access

import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.file.access.FileAccessChecker
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.inquiry.repository.InquiryRepository
import team.inreok.getiserver.domain.member.query.InquiryAssigneeCandidateQueryPort

/**
 * 문의 첨부파일의 다운로드 권한을 판정한다. `ownerId`는 파일이 연결된 문의의 ID다(요구사항
 * §16/§44). 본인이 작성한 문의의 첨부파일이거나, 개발자(문의 관리 권한)면 허용한다.
 *
 * 개발자 여부 판정에 [InquiryAssigneeCandidateQueryPort]를 재사용한다 -- 원래 담당자 지정
 * 대상(§32)을 검증하려고 만든 계약이지만, "이 회원의 역할이 무엇인가"라는 같은 조회이므로 거의
 * 동일한 목적의 Named Interface를 또 만들지 않았다.
 *
 * `FileAccessChecker`를 File 도메인이 소유하고 여기서 구현하는 이유는 Module 순환을 피하기
 * 위해서다(해당 Interface의 Class 주석 참고). 의존 방향은 `domain.inquiry -> domain.file`이다.
 */
@Component
class InquiryFileAccessChecker(
    private val inquiryRepository: InquiryRepository,
    private val inquiryAssigneeCandidateQueryPort: InquiryAssigneeCandidateQueryPort,
) : FileAccessChecker {
    override val ownerType: FileOwnerType = FileOwnerType.INQUIRY

    override fun canDownload(
        requesterId: Long,
        ownerId: Long,
    ): Boolean {
        // 문의가 없으면(삭제됐거나 존재하지 않으면) 접근을 거부한다 -- 없는 것과 같이 다룬다.
        val inquiry = inquiryRepository.findById(ownerId).orElse(null) ?: return false
        return inquiry.authorMemberId == requesterId ||
            inquiryAssigneeCandidateQueryPort.findById(requesterId)?.roles?.contains(DEVELOPER_ROLE) == true
    }

    private companion object {
        const val DEVELOPER_ROLE = "DEVELOPER"
    }
}
