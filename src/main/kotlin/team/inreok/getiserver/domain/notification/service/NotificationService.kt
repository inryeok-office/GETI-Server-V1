package team.inreok.getiserver.domain.notification.service

import org.springframework.data.domain.Pageable
import team.inreok.getiserver.domain.notification.dto.NotificationCreateCommand
import team.inreok.getiserver.domain.notification.dto.NotificationListResponse
import team.inreok.getiserver.domain.notification.dto.NotificationReadRequest
import team.inreok.getiserver.domain.notification.dto.NotificationReadResponse
import team.inreok.getiserver.domain.notification.dto.UnreadNotificationCountResponse
import team.inreok.getiserver.domain.notification.entity.type.NotificationType

interface NotificationService {
    /**
     * 내 알림 목록이다. `unreadOnly=true`면 읽지 않은 알림만, `notificationType`이 null이면 종류
     * 조건을 적용하지 않는다. 정렬은 최신순으로 고정이라 `pageable`의 Sort는 사용하지 않는다.
     *
     * 응답의 `unreadCount`는 Filter와 무관한 전체 미읽음 수다(Header Badge 용도).
     */
    fun list(
        memberId: Long,
        unreadOnly: Boolean,
        notificationType: NotificationType?,
        pageable: Pageable,
    ): NotificationListResponse

    fun countUnread(memberId: Long): UnreadNotificationCountResponse

    /**
     * 알림을 읽음 처리한다. `scope=SINGLE`이면 한 건, `scope=ALL`이면 본인의 읽지 않은 알림
     * 전체가 대상이다. 이미 읽은 알림은 아무것도 바꾸지 않고 `updatedCount`에도 세지 않는다(멱등).
     *
     * `scope=SINGLE`인데 `notificationId`가 없으면
     * [team.inreok.getiserver.domain.notification.exception.NotificationIdRequiredException](400),
     * 알림이 없거나 이미 삭제됐으면
     * [team.inreok.getiserver.domain.notification.exception.NotificationNotFoundException](404),
     * 다른 사용자의 알림이면
     * [team.inreok.getiserver.domain.notification.exception.NotificationAccessDeniedException](403).
     */
    fun read(
        memberId: Long,
        request: NotificationReadRequest,
    ): NotificationReadResponse

    /**
     * 알림 한 건을 Soft Delete한다. 삭제된 알림은 목록·읽지 않은 개수·읽음 처리 대상에서 모두
     * 빠진다. 없거나 이미 삭제된 알림은 404, 다른 사용자의 알림은 403이다(멱등하지 않다 --
     * 저장소의 다른 DELETE API와 같은 정책).
     */
    fun delete(
        memberId: Long,
        notificationId: Long,
    )

    /**
     * 인앱 알림을 생성하고 생성된 알림 ID를 반환한다. **REST로 노출되지 않는 Module 내부
     * 계약**이며, Domain Event를 수신해 알림을 만드는 후속 작업이 이 Method를 호출한다.
     *
     * **항상 새 Transaction에서 독립적으로 Commit되며(`REQUIRES_NEW`), 호출자의 Transaction이
     * Rollback되어도 이미 만들어진 알림은 취소되지 않는다.** 이 Method는 원본 Transaction이
     * Commit된 뒤에 실행되는 `@TransactionalEventListener(AFTER_COMMIT)` Listener가 호출하는
     * 것을 전제로 하기 때문이다 -- 그 시점에는 이미 끝난 Transaction에 참여해 봐야 Insert가
     * Commit되지 않고 조용히 사라진다(구현체 KDoc 참고).
     *
     * 따라서 "본문 작업과 알림이 함께 Rollback되어야 하는" 용도로 일반 Service Method 안에서
     * 직접 호출하면 안 된다. 그런 요구가 생기면 Event를 발행해 `AFTER_COMMIT`에서 처리한다.
     */
    fun create(command: NotificationCreateCommand): Long
}
