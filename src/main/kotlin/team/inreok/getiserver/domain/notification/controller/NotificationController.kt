package team.inreok.getiserver.domain.notification.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.notification.dto.NotificationListResponse
import team.inreok.getiserver.domain.notification.dto.NotificationReadAllResponse
import team.inreok.getiserver.domain.notification.dto.NotificationReadResponse
import team.inreok.getiserver.domain.notification.dto.UnreadNotificationCountResponse
import team.inreok.getiserver.domain.notification.entity.type.NotificationType
import team.inreok.getiserver.domain.notification.service.NotificationService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(
    name = "Notification - 인앱 알림",
    description = "내 인앱 알림을 조회하고 읽음 처리한다. 필요 권한: 인증된 사용자(학생, 교사, 개발자).",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
// SecurityConfig가 /api/v1/notifications 이하를 인증 필수로 두므로, 여기 도달했다는 것은 인증을
// 이미 통과했다는 뜻이다. 조회·처리 대상을 항상 요청자 본인의 알림으로 한정하는 소유권 검증은
// Role로 알 수 없어 NotificationService가 별도로 수행한다.
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val notificationService: NotificationService,
) {
    @Operation(
        summary = "내 알림 목록 조회",
        description = """
            요청한 사용자 본인의 알림만 조회한다. `isRead`, `type`으로 필터링할 수 있고 정렬은
            최신순(createdAt DESC, 같은 시각이면 id DESC)으로 고정이라 `sort` 파라미터는 무시한다.
            목록 기본값은 page=0, size=20이며 최대 size=100이다.

            각 항목의 `targetAvailable`/`targetUnavailableReason`/`deepLink`는 저장된 값이 아니라
            원본 리소스(공고·프로그램)의 삭제·공개 상태를 서버가 확인해 계산한 값이다 — 클라이언트가
            원본 상태를 직접 판단하지 않게 하기 위함이다. 원본이 삭제돼도 알림 자체는 사라지지 않고
            `targetAvailable=false`, `targetUnavailableReason=DELETED`로 내려간다. 접근 가능 여부를
            아직 판정하지 않는 대상(공고·프로그램 외)은 `targetAvailable=false`, 이유는 null이다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공(결과가 없으면 빈 목록)"),
        SwaggerApiResponse(responseCode = "400", description = "type 값이 올바르지 않음 (TYPE_MISMATCH)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping
    fun listNotifications(
        authentication: Authentication,
        @Parameter(description = "읽음 여부 필터(선택). 생략하면 읽음·읽지 않음을 모두 조회한다.")
        @RequestParam(required = false)
        isRead: Boolean?,
        @Parameter(description = "알림 종류 필터(선택)")
        @RequestParam(required = false)
        type: NotificationType?,
        @Parameter(description = "Pagination(page: 0부터 시작, size: 기본 20, 최대 100). sort는 무시된다.")
        pageable: Pageable,
    ): ApiResponse<NotificationListResponse> {
        val memberId = authentication.principal as Long
        return ApiResponse.of(notificationService.list(memberId, isRead, type, pageable))
    }

    @Operation(
        summary = "읽지 않은 알림 개수 조회",
        description = "요청한 사용자 본인의 읽지 않은 알림 개수를 반환한다. 알림이 없으면 0이다.",
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping("/unread-count")
    fun countUnread(authentication: Authentication): ApiResponse<UnreadNotificationCountResponse> {
        val memberId = authentication.principal as Long
        return ApiResponse.of(notificationService.countUnread(memberId))
    }

    @Operation(
        summary = "단일 알림 읽음 처리",
        description = """
            본인의 알림 한 건을 읽음 처리한다. 이미 읽은 알림을 다시 호출해도 오류가 아니며,
            처음 읽은 시각(`readAt`)이 그대로 유지된다(멱등). 다른 사용자의 알림이면 403이다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "읽음 처리 성공(이미 읽은 알림 포함)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "다른 사용자의 알림 (NOTIFICATION_ACCESS_DENIED)"),
        SwaggerApiResponse(responseCode = "404", description = "알림이 없음 (NOTIFICATION_NOT_FOUND)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PatchMapping("/{notificationId}/read")
    fun markAsRead(
        authentication: Authentication,
        @Parameter(description = "읽음 처리할 알림 ID") @PathVariable notificationId: Long,
    ): ApiResponse<NotificationReadResponse> {
        val memberId = authentication.principal as Long
        return ApiResponse.of(notificationService.markAsRead(memberId, notificationId))
    }

    @Operation(
        summary = "전체 알림 읽음 처리",
        description = """
            요청한 사용자 본인의 읽지 않은 알림을 모두 읽음 처리한다. 이미 모두 읽은 상태여도
            오류가 아니며 `updatedCount=0`으로 정상 응답한다. 이번 요청으로 갱신된 알림은 모두
            같은 `readAt` 값을 갖는다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "읽음 처리 성공(갱신 대상이 없으면 updatedCount=0)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PatchMapping("/read-all")
    fun markAllAsRead(authentication: Authentication): ApiResponse<NotificationReadAllResponse> {
        val memberId = authentication.principal as Long
        return ApiResponse.of(notificationService.markAllAsRead(memberId))
    }
}
