package team.inreok.getiserver.domain.inquiry.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.inquiry.query.InquiryNotificationTargetQueryPort
import team.inreok.getiserver.domain.inquiry.query.InquiryNotificationTargetSnapshot
import team.inreok.getiserver.domain.inquiry.repository.InquiryRepository

/**
 * 다른 Domain Module(notification)에 공개된 조회 계약([InquiryNotificationTargetQueryPort])의
 * 구현이다. `JobNotificationTargetQueryPortImpl`/`ProgramNotificationTargetQueryPortImpl`과 같은
 * 이유로 `InquiryServiceImpl`에 합치지 않고 분리한다.
 */
@Service
class InquiryNotificationTargetQueryPortImpl(
    private val inquiryRepository: InquiryRepository,
) : InquiryNotificationTargetQueryPort {
    @Transactional(readOnly = true)
    override fun findAllByIds(inquiryIds: Set<Long>): Map<Long, InquiryNotificationTargetSnapshot> {
        if (inquiryIds.isEmpty()) return emptyMap()
        return inquiryRepository
            .findAllById(inquiryIds)
            .mapNotNull { inquiry ->
                val inquiryId = inquiry.id ?: return@mapNotNull null
                inquiryId to
                    InquiryNotificationTargetSnapshot(
                        inquiryId = inquiryId,
                        authorMemberId = inquiry.authorMemberId,
                    )
            }.toMap()
    }
}
