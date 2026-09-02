package team.inreok.getiserver.domain.notification.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.notification.dto.NotificationDeviceRegisterRequest
import team.inreok.getiserver.domain.notification.dto.NotificationDeviceResponse
import team.inreok.getiserver.domain.notification.dto.NotificationListResponse
import team.inreok.getiserver.domain.notification.dto.NotificationReadRequest
import team.inreok.getiserver.domain.notification.dto.NotificationReadResponse
import team.inreok.getiserver.domain.notification.dto.NotificationSettingResponse
import team.inreok.getiserver.domain.notification.dto.NotificationSettingUpdateRequest
import team.inreok.getiserver.domain.notification.dto.UnreadNotificationCountResponse
import team.inreok.getiserver.domain.notification.entity.type.NotificationType
import team.inreok.getiserver.domain.notification.service.NotificationDeviceService
import team.inreok.getiserver.domain.notification.service.NotificationService
import team.inreok.getiserver.domain.notification.service.NotificationSettingService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(
    name = "Notification - 인앱 알림",
    description =
        "내 인앱 알림을 조회하고 읽음 처리하거나 삭제하며, 푸시 기기를 등록·해제하고 푸시 알림 설정을 " +
            "조회·변경한다. 필요 권한: 인증된 사용자(학생, 교사, 개발자).",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
// SecurityConfig가 /api/v1/notifications 이하를 인증 필수로 두므로, 여기 도달했다는 것은 인증을
// 이미 통과했다는 뜻이다. 조회·처리 대상을 항상 요청자 본인의 알림/기기/설정으로 한정하는 소유권
// 검증은 Role로 알 수 없어 각 Service가 별도로 수행한다.
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val notificationService: NotificationService,
    private val notificationDeviceService: NotificationDeviceService,
    private val notificationSettingService: NotificationSettingService,
) {
    @Operation(
        summary = "내 알림 목록 조회",
        description = """
            요청한 사용자 본인의 알림만 조회한다. `unreadOnly`, `notificationType`으로 필터링할 수
            있고 정렬은 최신순(createdAt DESC, 같은 시각이면 id DESC)으로 고정이라 `sort` 파라미터는
            무시한다. 목록 기본값은 page=0, size=20이며 최대 size=100이다.

            응답의 `unreadCount`는 Header Badge용 값이라 `unreadOnly`/`notificationType` 필터나
            현재 Page와 무관하게 **요청자 본인의 읽지 않은 알림 전체 개수**다.

            각 항목의 `targetAvailable`/`targetUnavailableReason`/`deepLink`는 저장된 값이 아니라
            원본 리소스의 삭제·공개·소유 상태를 서버가 확인해 계산한 값이다 — 클라이언트가 원본
            상태를 직접 판단하지 않게 하기 위함이다. 원본이 삭제돼도 알림 자체는 사라지지 않고
            `targetAvailable=false`, `targetUnavailableReason=DELETED`로 내려간다. 접근 가능 여부를
            아직 판정하지 않는 대상(회원 승인 결과, 포트폴리오 요청)은 `targetAvailable=false`,
            이유는 null이다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공(결과가 없으면 빈 목록)"),
        SwaggerApiResponse(responseCode = "400", description = "notificationType 값이 올바르지 않음 (TYPE_MISMATCH)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping
    fun listNotifications(
        authentication: Authentication,
        @Parameter(description = "true면 읽지 않은 알림만 조회한다. 생략하거나 false면 읽음·읽지 않음을 모두 조회한다.")
        @RequestParam(required = false, defaultValue = "false")
        unreadOnly: Boolean,
        @Parameter(description = "알림 종류 필터(선택)")
        @RequestParam(required = false)
        notificationType: NotificationType?,
        @Parameter(description = "Pagination(page: 0부터 시작, size: 기본 20, 최대 100). sort는 무시된다.")
        pageable: Pageable,
    ): ApiResponse<NotificationListResponse> {
        val memberId = authentication.principal as Long
        return ApiResponse.of(notificationService.list(memberId, unreadOnly, notificationType, pageable))
    }

    @Operation(
        summary = "읽지 않은 알림 개수 조회",
        description = """
            요청한 사용자 본인의 읽지 않은 알림 개수를 반환한다. 알림이 없으면 0이다. 목록 응답의
            `unreadCount`와 같은 값이며, Badge만 갱신할 때 목록 한 Page를 통째로 받지 않아도 되도록
            분리해 둔 Endpoint다.
        """,
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
        summary = "알림 읽음 처리",
        description = """
            `scope=SINGLE`이면 `notificationId`가 가리키는 알림 한 건을, `scope=ALL`이면 요청자
            본인의 읽지 않은 알림 전체를 읽음 처리한다.

            - `scope=SINGLE`인데 `notificationId`가 없으면 400이다.
            - `scope=ALL`에서는 `notificationId`를 함께 보내도 무시한다.
            - 이미 읽은 알림을 다시 처리해도 오류가 아니며, 처음 읽은 시각(`readAt`)이 그대로
              유지되고 `updatedCount`에 세지 않는다(멱등).
            - `updatedCount`는 이번 요청으로 실제 상태가 바뀐 알림 수, `unreadCount`는 처리 후 남은
              읽지 않은 알림 수다.
            - `readAt`은 `scope=SINGLE`이면 대상 알림의 읽은 시각, `scope=ALL`이면 이번 요청으로
              갱신한 시각이다. `scope=ALL`인데 갱신 대상이 없었으면 null이다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "읽음 처리 성공(이미 읽은 알림 포함)"),
        SwaggerApiResponse(
            responseCode = "400",
            description = "scope=SINGLE인데 notificationId가 없음 (NOTIFICATION_ID_REQUIRED)",
        ),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "다른 사용자의 알림 (NOTIFICATION_ACCESS_DENIED)"),
        SwaggerApiResponse(responseCode = "404", description = "알림이 없거나 이미 삭제됨 (NOTIFICATION_NOT_FOUND)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PatchMapping("/read")
    fun read(
        authentication: Authentication,
        @Valid @RequestBody request: NotificationReadRequest,
    ): ApiResponse<NotificationReadResponse> {
        val memberId = authentication.principal as Long
        return ApiResponse.of(notificationService.read(memberId, request))
    }

    @Operation(
        summary = "알림 삭제",
        description = """
            요청자 본인의 알림 한 건을 삭제한다. 삭제한 알림은 목록·읽지 않은 개수·읽음 처리
            대상에서 모두 빠진다. 읽지 않은 알림을 삭제하면 읽지 않은 개수도 함께 줄어든다.

            없거나 이미 삭제한 알림을 다시 삭제하면 404다(멱등하지 않다). 다른 사용자의 알림이면
            403이다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "204", description = "삭제 성공(응답 본문 없음)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "다른 사용자의 알림 (NOTIFICATION_ACCESS_DENIED)"),
        SwaggerApiResponse(responseCode = "404", description = "알림이 없거나 이미 삭제됨 (NOTIFICATION_NOT_FOUND)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @DeleteMapping("/{notificationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        authentication: Authentication,
        @Parameter(description = "삭제할 알림 ID", example = "1") @PathVariable notificationId: Long,
    ) {
        val memberId = authentication.principal as Long
        notificationService.delete(memberId, notificationId)
    }

    // ---------- 푸시 기기 (Issue #189) ----------

    @Operation(
        summary = "푸시 기기 등록/갱신",
        description = """
            요청자 본인 명의로 기기를 등록한다. 같은 deviceKey로 다시 등록하면 기존 Row를 갱신하고
            (재등록), 다른 회원이 이미 등록한 deviceKey면 이 요청의 회원으로 소유권이 이전된다 --
            deviceKey를 실제 물리/논리 기기 식별자로 취급하고 최신 로그인 사용자의 소유권으로 안전하게
            이전하는 것이 확정 정책이며, 이전 소유자에게 별도 알림·거부 절차는 없다.

            deviceKey는 안정적인 기기/앱 설치 식별자이고, pushToken은 FCM/APNs Token으로 만료·재발급될
            수 있어 별도 값으로 분리되어 있다. pushToken은 어떤 응답에도 포함되지 않는다. 실제 Provider
            (FCM/APNs) 발송 연동은 이 API의 범위가 아니다(Issue #190).
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "201", description = "등록/갱신 성공"),
        SwaggerApiResponse(responseCode = "400", description = "요청 값 검증 실패 (VALIDATION_FAILED)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PostMapping("/devices")
    @ResponseStatus(HttpStatus.CREATED)
    fun registerDevice(
        authentication: Authentication,
        @Valid @RequestBody request: NotificationDeviceRegisterRequest,
    ): ApiResponse<NotificationDeviceResponse> {
        val memberId = authentication.principal as Long
        return ApiResponse.of(notificationDeviceService.register(memberId, request))
    }

    @Operation(
        summary = "푸시 기기 해제",
        description = """
            요청자 본인이 등록한 기기 한 건을 해제(삭제)한다. deviceKey가 존재하지 않으면 404, 다른
            회원이 등록한 기기면 403이다(다른 회원의 deviceKey 존재 여부를 알려주지 않기 위해 Message에
            소유자 정보를 담지 않는다, 알림 삭제 API와 같은 정책).
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "204", description = "해제 성공(응답 본문 없음)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(
            responseCode = "403",
            description = "다른 회원이 등록한 기기 (NOTIFICATION_DEVICE_ACCESS_DENIED)",
        ),
        SwaggerApiResponse(responseCode = "404", description = "등록되지 않은 기기 (NOTIFICATION_DEVICE_NOT_FOUND)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @DeleteMapping("/devices/{deviceKey}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unregisterDevice(
        authentication: Authentication,
        @Parameter(description = "해제할 기기 식별자(deviceKey)", example = "a1b2c3d4-5678-90ab-cdef-device-uuid")
        @PathVariable
        deviceKey: String,
    ) {
        val memberId = authentication.principal as Long
        notificationDeviceService.unregister(memberId, deviceKey)
    }

    // ---------- 푸시 알림 설정 (Issue #189) ----------

    @Operation(
        summary = "푸시 알림 설정 조회",
        description = """
            요청자 본인의 푸시 알림 수신 여부(pushEnabled)를 조회한다. NotificationType별 세분화된
            설정은 없고 회원당 값 하나이며, 한 번도 설정한 적 없으면 기본값(true)을 반환한다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping("/settings")
    fun getSettings(authentication: Authentication): ApiResponse<NotificationSettingResponse> {
        val memberId = authentication.principal as Long
        return ApiResponse.of(notificationSettingService.getSettings(memberId))
    }

    @Operation(
        summary = "푸시 알림 설정 변경",
        description = """
            요청자 본인의 푸시 알림 수신 여부를 변경한다. false로 꺼도 등록된 기기(Row)는 삭제되지
            않고 인앱 알림 생성도 계속되며, Push 발송만 멈춘다(실제 발송 연동은 Issue #190 범위).
            이전 값과 같은 값을 다시 보내도 오류 없이 성공한다(멱등).
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "변경 성공"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PatchMapping("/settings")
    fun updateSettings(
        authentication: Authentication,
        @RequestBody request: NotificationSettingUpdateRequest,
    ): ApiResponse<NotificationSettingResponse> {
        val memberId = authentication.principal as Long
        return ApiResponse.of(notificationSettingService.updateSettings(memberId, request.pushEnabled))
    }
}
