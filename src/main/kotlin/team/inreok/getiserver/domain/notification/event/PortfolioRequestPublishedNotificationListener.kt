package team.inreok.getiserver.domain.notification.event

import org.slf4j.LoggerFactory
import org.springframework.core.task.TaskExecutor
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import team.inreok.getiserver.domain.notification.dto.NotificationCreateCommand
import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetType
import team.inreok.getiserver.domain.notification.entity.type.NotificationType
import team.inreok.getiserver.domain.notification.service.NotificationService
import team.inreok.getiserver.domain.portfolio.event.PortfolioRequestPublishedEvent
import team.inreok.getiserver.domain.portfolio.query.PortfolioRequestTargetQueryPort
import java.time.format.DateTimeFormatter

/**
 * Portfolio가 발행하는 [PortfolioRequestPublishedEvent]를 받아 대상 학생들에게 인앱 알림을
 * 만든다(Issue #331). 구조와 실패 처리 방침은 [ProgramDeletedNotificationListener]와 같다 --
 * 수신자 수가 정해져 있지 않아 실제 생성은 전용 `notificationTaskExecutor`로 넘기고, 한 수신자의
 * 저장 실패가 나머지 수신자에게 번지지 않도록 개별로 감싼다.
 *
 * `targetType`은 [NotificationTargetType.PORTFOLIO_REQUEST]로 둔다. 접근 판정과 Deep Link는
 * [team.inreok.getiserver.domain.notification.service.NotificationTargetResolver]가 계산한다
 * (Issue #332). Resolver는 이 Listener가 대상 학생에게만 알림을 만든다는 전제로 역할 예외를 두지
 * 않으므로, 수신자 범위를 넓히면 Resolver 판정도 함께 바꿔야 한다.
 *
 * Discord 발송 대상에는 넣지 않는다. 기존 Discord Listener(`job`/`program`/`inquiry`)는 모두 공개
 * 채널 브로드캐스트인 반면 수합 요청은 지정된 학생만 열람할 수 있는 대상 지정 리소스라, 채널에
 * 올리면 대상이 아닌 인원에게 요청 제목이 노출된다(Issue #331에서 확정).
 */
@Component
class PortfolioRequestPublishedNotificationListener(
    private val portfolioRequestTargetQueryPort: PortfolioRequestTargetQueryPort,
    private val notificationService: NotificationService,
    private val notificationTaskExecutor: TaskExecutor,
) {
    private val log = LoggerFactory.getLogger(PortfolioRequestPublishedNotificationListener::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onPortfolioRequestPublished(event: PortfolioRequestPublishedEvent) {
        notificationTaskExecutor.execute { createNotifications(event) }
    }

    private fun createNotifications(event: PortfolioRequestPublishedEvent) {
        val studentMemberIds =
            runCatching { portfolioRequestTargetQueryPort.findTargetStudentMemberIds(event.requestId) }
                .getOrElse { ex ->
                    log.error("공개된 포트폴리오 수합 요청의 대상 학생 조회 실패(requestId={})", event.requestId, ex)
                    return
                }

        studentMemberIds.distinct().forEach { memberId ->
            runCatching { createNotification(event, memberId) }
                .onFailure { ex ->
                    log.error(
                        "포트폴리오 수합 요청 공개 알림 생성 중 처리되지 않은 오류(requestId={}, memberId={})",
                        event.requestId,
                        memberId,
                        ex,
                    )
                }
        }
    }

    private fun createNotification(
        event: PortfolioRequestPublishedEvent,
        recipientMemberId: Long,
    ) {
        notificationService.create(
            NotificationCreateCommand(
                recipientMemberId = recipientMemberId,
                type = NotificationType.PORTFOLIO_REQUEST_PUBLISHED,
                title = "새로운 포트폴리오 수합 요청이 등록되었습니다",
                content = "\"${event.title}\" 요청이 등록되었습니다. ${DUE_AT_FORMAT.format(event.dueAt)}까지 제출해 주세요.",
                // 공개(DRAFT -> PUBLISHED)는 한 요청의 생애주기에 최대 한 번만 일어나므로
                // (PortfolioRequestServiceImpl.allowedTransitions 참고) requestId 자체가 안정적인
                // 식별자다. 같은 Event가 중복 수신되면 수신자별로 dedup된다.
                sourceEventType = SOURCE_EVENT_TYPE,
                sourceEventId = event.requestId,
                targetType = NotificationTargetType.PORTFOLIO_REQUEST,
                targetId = event.requestId,
            ),
        )
    }

    private companion object {
        // Notification Idempotency Identity(Issue #193)의 sourceEventType. 원본 Domain Event Class
        // 이름을 그대로 쓴다(InquiryAnsweredNotificationListener와 같은 이유).
        const val SOURCE_EVENT_TYPE = "PortfolioRequestPublishedEvent"

        // 사용자에게 보이는 날짜 표기는 저장소의 기존 사용자 노출 문구와 같은 형식을 쓴다
        // (DiscordJobEmbedBuilder.DATE_FORMAT).
        val DUE_AT_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
