package team.inreok.getiserver.domain.inquiry.query

import org.springframework.modulith.NamedInterface

/**
 * `notification` Module이 알림의 이동 대상(문의)이 아직 접근 가능한지 계산할 때 쓰는 공개
 * 계약이다(Issue #187). `notification`은 이 Interface를 통해서만 Inquiry를 읽고, `Inquiry`
 * Entity나 `InquiryRepository`를 직접 참조하지 않는다.
 *
 * 이 계약이 `notification`이 아니라 `inquiry`에 있는 이유는
 * [team.inreok.getiserver.domain.job.query.JobNotificationTargetQueryPort]와 같다 -- 반대로 두면
 * `inquiry -> notification` 의존이 생기는데 `notification`이 이미 `inquiry.event
 * .InquiryAnsweredEvent`를 구독하고 있어 `ModularityTest`가 순환 의존으로 실패한다.
 *
 * 접근 가능 여부와 그 이유(DELETED/FORBIDDEN)를 고르는 판단은 `notification`이 수행한다. 판정
 * Enum을 여기서 돌려주면 `inquiry`가 `notification`의 타입을 알아야 해서 다시 순환이 생긴다.
 */
@NamedInterface
interface InquiryNotificationTargetQueryPort {
    /**
     * 존재하지 않는 id는 결과 Map에서 빠진다(다른 Notification Target Port와 같은 관례).
     *
     * 목록 API가 한 번에 최대 100건을 반환하므로 단건이 아닌 배치 조회로 둔다(N+1 방지).
     */
    fun findAllByIds(inquiryIds: Set<Long>): Map<Long, InquiryNotificationTargetSnapshot>
}

/**
 * `inquiries`는 Soft Delete Column이 없어 "삭제됨"은 Row가 없는 것과 같다. 그래서 상태
 * 필드 없이 소유자 판정에 필요한 값만 담는다 -- 문의는 상태(RECEIVED/ANSWERED 등)와 무관하게
 * 작성자 본인이면 언제나 열람할 수 있다(`InquiryController.getInquiry`).
 */
@NamedInterface
data class InquiryNotificationTargetSnapshot(
    val inquiryId: Long,
    val authorMemberId: Long,
)
