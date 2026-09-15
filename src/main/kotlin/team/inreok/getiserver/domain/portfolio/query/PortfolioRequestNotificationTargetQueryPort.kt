package team.inreok.getiserver.domain.portfolio.query

import org.springframework.modulith.NamedInterface

/**
 * `notification` Module이 알림의 이동 대상(포트폴리오 수합 요청)이 아직 접근 가능한지 계산할 때
 * 쓰는 공개 계약이다(Issue #332). `notification`은 이 Interface를 통해서만 수합 요청을 읽고,
 * `PortfolioRequest` Entity나 Repository를 직접 참조하지 않는다.
 *
 * 이 계약이 `notification`이 아니라 `portfolio`에 있는 이유는 [PortfolioRequestTargetQueryPort]와
 * 같다 -- `notification`이 이미 `PortfolioRequestPublishedEvent`를 구독하고 있어, 반대로 두면
 * `ModularityTest`가 순환 의존으로 실패한다.
 *
 * 접근 가능 여부와 그 이유(DELETED/NOT_VISIBLE/FORBIDDEN)를 고르는 판단은 `notification`이 수행한다.
 * 판정 Enum을 여기서 돌려주면 `portfolio`가 `notification`의 타입을 알아야 해서 다시 순환이 생긴다.
 */
@NamedInterface
interface PortfolioRequestNotificationTargetQueryPort {
    /**
     * 존재하지 않는 id는 결과 Map에서 빠진다(다른 Notification Target Port와 같은 관례).
     *
     * 수합 요청은 대상 학생만 열람할 수 있어 공개 리소스(Job/Program)와 달리 요청자에 따라 결과가
     * 달라지므로 [viewerMemberId]를 함께 받는다. 대상 학생 id 전체를 Snapshot에 담지 않는 이유는
     * 대상 인원 수가 정해져 있지 않기 때문이다. 목록 API가 한 번에 최대 100건을 반환하므로 배치
     * 조회로 둔다(N+1 방지).
     */
    fun findAllByIds(
        requestIds: Set<Long>,
        viewerMemberId: Long,
    ): Map<Long, PortfolioRequestNotificationTargetSnapshot>
}

/**
 * [status]는 `PortfolioRequestStatus`의 이름이다. `JobNotificationTargetSnapshot`처럼 Enum 대신
 * 문자열로 둬 `notification`이 `portfolio`의 `entity` 내부 타입에 의존하지 않게 한다. [deleted]는
 * `deletedAt`이 채워졌는지, [targetedToViewer]는 요청자가 `portfolio_request_targets`의 대상
 * 학생인지를 뜻한다. 삭제·DRAFT 요청도 걸러내지 않고 그대로 돌려준다.
 */
@NamedInterface
data class PortfolioRequestNotificationTargetSnapshot(
    val requestId: Long,
    val status: String,
    val deleted: Boolean,
    val targetedToViewer: Boolean,
)
