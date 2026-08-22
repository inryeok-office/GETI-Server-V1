package team.inreok.getiserver.domain.inquiry.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.inquiry.query.InquiryDiscordPayloadQueryPort
import team.inreok.getiserver.domain.inquiry.query.InquiryDiscordPayloadSnapshot
import team.inreok.getiserver.domain.inquiry.repository.InquiryRepository
import team.inreok.getiserver.domain.member.query.InquiryMemberSnapshotQueryPort

/**
 * 다른 Domain Module(notification)에 공개된 조회 계약([InquiryDiscordPayloadQueryPort])의
 * 구현이다. `InquiryServiceImpl`이 이미 크고 이 조회는 문의 Use Case와 무관하므로 분리한다.
 *
 * 작성자 이름은 `member` Module의 공개 Port로 읽는다 -- `domain.inquiry`는 이미
 * `InquiryAssigneeCandidateQueryPort`/`InquiryMemberSnapshotQueryPort`로 `member.query`에
 * 의존하고 있어 새 Module 의존이 생기지 않는다.
 */
@Service
class InquiryDiscordPayloadQueryPortImpl(
    private val inquiryRepository: InquiryRepository,
    private val inquiryMemberSnapshotQueryPort: InquiryMemberSnapshotQueryPort,
) : InquiryDiscordPayloadQueryPort {
    @Transactional(readOnly = true)
    override fun findById(inquiryId: Long): InquiryDiscordPayloadSnapshot? {
        val inquiry = inquiryRepository.findById(inquiryId).orElse(null) ?: return null
        return InquiryDiscordPayloadSnapshot(
            inquiryId = requireNotNull(inquiry.id) { "저장된 Inquiry는 id를 가져야 합니다." },
            category = inquiry.type.name,
            // 탈퇴 등으로 조회되지 않으면 null이다. Bot Schema에서 선택 필드라 생략된다.
            // Port가 배치 조회만 제공하므로 단건도 1개짜리 Set으로 부른다.
            requesterName =
                inquiryMemberSnapshotQueryPort
                    .findAllByIds(setOf(inquiry.authorMemberId))[inquiry.authorMemberId]
                    ?.name,
            createdAt = inquiry.createdAt,
        )
    }

    @Transactional(readOnly = true)
    override fun findDisplayNamesByIds(inquiryIds: Set<Long>): Map<Long, String> {
        if (inquiryIds.isEmpty()) return emptyMap()
        // 제목이 아니라 유형(InquiryType.name)이다 -- 제목·작성자 이름은 §40에 따라 Module 밖으로
        // 내보내지 않는다(Port KDoc 참고).
        return inquiryRepository
            .findAllById(inquiryIds)
            .associate { requireNotNull(it.id) { "저장된 Inquiry는 id를 가져야 합니다." } to it.type.name }
    }
}
