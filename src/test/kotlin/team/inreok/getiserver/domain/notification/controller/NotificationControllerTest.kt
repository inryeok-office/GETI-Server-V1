package team.inreok.getiserver.domain.notification.controller

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.Pageable
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.notification.dto.NotificationDeviceRegisterRequest
import team.inreok.getiserver.domain.notification.dto.NotificationDeviceResponse
import team.inreok.getiserver.domain.notification.dto.NotificationListResponse
import team.inreok.getiserver.domain.notification.dto.NotificationReadRequest
import team.inreok.getiserver.domain.notification.dto.NotificationReadResponse
import team.inreok.getiserver.domain.notification.dto.NotificationReadScope
import team.inreok.getiserver.domain.notification.dto.NotificationSettingResponse
import team.inreok.getiserver.domain.notification.dto.NotificationSummaryResponse
import team.inreok.getiserver.domain.notification.dto.UnreadNotificationCountResponse
import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetType
import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetUnavailableReason
import team.inreok.getiserver.domain.notification.entity.type.NotificationType
import team.inreok.getiserver.domain.notification.entity.type.PushPlatform
import team.inreok.getiserver.domain.notification.exception.NotificationAccessDeniedException
import team.inreok.getiserver.domain.notification.exception.NotificationDeviceAccessDeniedException
import team.inreok.getiserver.domain.notification.exception.NotificationDeviceNotFoundException
import team.inreok.getiserver.domain.notification.exception.NotificationIdRequiredException
import team.inreok.getiserver.domain.notification.exception.NotificationNotFoundException
import team.inreok.getiserver.domain.notification.service.NotificationDeviceService
import team.inreok.getiserver.domain.notification.service.NotificationService
import team.inreok.getiserver.domain.notification.service.NotificationSettingService
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.web.WebPageableConfig
import java.time.LocalDateTime

// SecurityConfig를 명시적으로 Import해 /api/v1/notifications 이하가 실제로 인증을 요구하는지(401)까지
// 검증한다(ProgramControllerTest와 동일한 방식). WebPageableConfig도 함께 Import해야 최대 Page
// Size(100) 강제가 이 Slice에서도 실제로 동작한다 — @WebMvcTest는 일반 @Configuration을 포함하지
// 않기 때문이다.
@WebMvcTest(controllers = [NotificationController::class])
@Import(team.inreok.getiserver.global.security.NormalSecurityTestConfig::class, WebPageableConfig::class)
@EnableWebSecurity
class NotificationControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var notificationService: NotificationService

        @MockitoBean
        private lateinit var notificationDeviceService: NotificationDeviceService

        @MockitoBean
        private lateinit var notificationSettingService: NotificationSettingService

        @MockitoBean
        private lateinit var jwtTokenProvider: JwtTokenProvider

        private val memberId = 1L

        private fun studentAuth() =
            authentication(
                UsernamePasswordAuthenticationToken(
                    memberId,
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_STUDENT")),
                ),
            )

        private fun anyPageable(): Pageable = any(Pageable::class.java) ?: Pageable.unpaged()

        // Kotlin non-null 파라미터에 bare any()를 쓰면 null 반환으로 NPE가 나므로 Elvis로 기본값을
        // 채운다(ProgramControllerTest.anyActionRequest()와 같은 이유).
        private fun anyReadRequest(): NotificationReadRequest =
            any(NotificationReadRequest::class.java) ?: NotificationReadRequest(NotificationReadScope.ALL)

        private fun anyDeviceRegisterRequest(): NotificationDeviceRegisterRequest =
            any(NotificationDeviceRegisterRequest::class.java)
                ?: NotificationDeviceRegisterRequest("device-key-1", "push-token-1", PushPlatform.ANDROID)

        private val summary =
            NotificationSummaryResponse(
                notificationId = 1L,
                notificationType = NotificationType.PROGRAM_PUBLISHED,
                title = "새 프로그램이 게시되었습니다",
                content = "AI 특강 모집이 시작되었습니다.",
                targetType = NotificationTargetType.PROGRAM,
                targetId = 123L,
                targetAvailable = true,
                targetUnavailableReason = null,
                deepLink = "/programs/123",
                read = false,
                readAt = null,
                createdAt = LocalDateTime.of(2026, 8, 7, 10, 0),
            )

        private fun listResponse(
            content: List<NotificationSummaryResponse> = listOf(summary),
            unreadCount: Long = 5L,
        ) = NotificationListResponse(
            content = content,
            page = 0,
            size = 20,
            totalElements = content.size.toLong(),
            totalPages = 1,
            first = true,
            last = true,
            unreadCount = unreadCount,
        )

        // ---------- 목록 ----------

        @Test
        fun `인증된 사용자는 자신의 알림 목록을 조회할 수 있다`() {
            given(notificationService.list(anyLong(), anyBoolean(), any(), anyPageable())).willReturn(listResponse())

            mockMvc
                .perform(get("/api/v1/notifications").with(studentAuth()))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].notificationId").value(1))
                .andExpect(jsonPath("$.data.content[0].notificationType").value("PROGRAM_PUBLISHED"))
                .andExpect(jsonPath("$.data.content[0].read").value(false))
                .andExpect(jsonPath("$.data.content[0].targetAvailable").value(true))
                .andExpect(jsonPath("$.data.content[0].deepLink").value("/programs/123"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.meta.requestId").exists())
        }

        @Test
        fun `목록 응답에 Badge용 전체 미읽음 수가 함께 내려간다`() {
            given(notificationService.list(anyLong(), anyBoolean(), any(), anyPageable()))
                .willReturn(listResponse(unreadCount = 7L))

            mockMvc
                .perform(get("/api/v1/notifications").with(studentAuth()))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.unreadCount").value(7))
        }

        @Test
        fun `대상이 삭제된 알림은 사유와 함께 내려간다`() {
            val deletedTarget =
                summary.copy(
                    targetAvailable = false,
                    targetUnavailableReason = NotificationTargetUnavailableReason.DELETED,
                    deepLink = null,
                )
            given(notificationService.list(anyLong(), anyBoolean(), any(), anyPageable()))
                .willReturn(listResponse(listOf(deletedTarget)))

            mockMvc
                .perform(get("/api/v1/notifications").with(studentAuth()))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.content[0].targetAvailable").value(false))
                .andExpect(jsonPath("$.data.content[0].targetUnavailableReason").value("DELETED"))
                .andExpect(jsonPath("$.data.content[0].deepLink").doesNotExist())
        }

        @Test
        fun `소유자가 아닌 대상은 FORBIDDEN 사유로 내려간다`() {
            val forbiddenTarget =
                summary.copy(
                    targetType = NotificationTargetType.INQUIRY,
                    targetAvailable = false,
                    targetUnavailableReason = NotificationTargetUnavailableReason.FORBIDDEN,
                    deepLink = null,
                )
            given(notificationService.list(anyLong(), anyBoolean(), any(), anyPageable()))
                .willReturn(listResponse(listOf(forbiddenTarget)))

            mockMvc
                .perform(get("/api/v1/notifications").with(studentAuth()))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.content[0].targetUnavailableReason").value("FORBIDDEN"))
        }

        @Test
        fun `인증 없이 알림 목록을 조회하면 401이다`() {
            mockMvc
                .perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))

            verify(notificationService, never()).list(anyLong(), anyBoolean(), any(), anyPageable())
        }

        @Test
        fun `요청한 사용자 본인의 ID로만 조회한다`() {
            given(notificationService.list(anyLong(), anyBoolean(), any(), anyPageable())).willReturn(listResponse())

            mockMvc
                .perform(get("/api/v1/notifications").with(studentAuth()))
                .andExpect(status().isOk)

            val memberIdCaptor = ArgumentCaptor.forClass(Long::class.javaObjectType)
            verify(notificationService).list(memberIdCaptor.capture() ?: 0L, anyBoolean(), any(), anyPageable())
            assertThat(memberIdCaptor.value).isEqualTo(memberId)
        }

        @Test
        fun `unreadOnly와 notificationType 필터를 전달한다`() {
            given(notificationService.list(anyLong(), anyBoolean(), any(), anyPageable())).willReturn(listResponse())

            mockMvc
                .perform(
                    get("/api/v1/notifications")
                        .param("unreadOnly", "true")
                        .param("notificationType", "PROGRAM_PUBLISHED")
                        .with(studentAuth()),
                ).andExpect(status().isOk)

            val unreadOnlyCaptor = ArgumentCaptor.forClass(Boolean::class.javaObjectType)
            val typeCaptor = ArgumentCaptor.forClass(NotificationType::class.java)
            verify(notificationService).list(
                anyLong(),
                unreadOnlyCaptor.capture() ?: false,
                typeCaptor.capture(),
                anyPageable(),
            )
            assertThat(unreadOnlyCaptor.value).isTrue
            assertThat(typeCaptor.value).isEqualTo(NotificationType.PROGRAM_PUBLISHED)
        }

        @Test
        fun `unreadOnly를 생략하면 false로 조회한다`() {
            given(notificationService.list(anyLong(), anyBoolean(), any(), anyPageable())).willReturn(listResponse())

            mockMvc
                .perform(get("/api/v1/notifications").with(studentAuth()))
                .andExpect(status().isOk)

            val unreadOnlyCaptor = ArgumentCaptor.forClass(Boolean::class.javaObjectType)
            verify(notificationService).list(anyLong(), unreadOnlyCaptor.capture() ?: false, any(), anyPageable())
            assertThat(unreadOnlyCaptor.value).isFalse
        }

        @Test
        fun `잘못된 알림 종류를 보내면 400이다`() {
            mockMvc
                .perform(
                    get("/api/v1/notifications")
                        .param("notificationType", "NOT_A_REAL_TYPE")
                        .with(studentAuth()),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("TYPE_MISMATCH"))
        }

        @Test
        fun `size가 최대값을 넘으면 100으로 잘린다`() {
            given(notificationService.list(anyLong(), anyBoolean(), any(), anyPageable())).willReturn(listResponse())

            mockMvc
                .perform(get("/api/v1/notifications").param("size", "500").with(studentAuth()))
                .andExpect(status().isOk)

            val pageableCaptor = ArgumentCaptor.forClass(Pageable::class.java)
            verify(notificationService).list(
                anyLong(),
                anyBoolean(),
                any(),
                pageableCaptor.capture() ?: Pageable.unpaged(),
            )
            assertThat(pageableCaptor.value.pageSize).isEqualTo(100)
        }

        // ---------- 읽지 않은 개수 ----------

        @Test
        fun `읽지 않은 알림 개수를 조회할 수 있다`() {
            given(notificationService.countUnread(memberId)).willReturn(UnreadNotificationCountResponse(3L))

            mockMvc
                .perform(get("/api/v1/notifications/unread-count").with(studentAuth()))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.unreadCount").value(3))
        }

        @Test
        fun `인증 없이 읽지 않은 개수를 조회하면 401이다`() {
            mockMvc
                .perform(get("/api/v1/notifications/unread-count"))
                .andExpect(status().isUnauthorized)

            verify(notificationService, never()).countUnread(anyLong())
        }

        // ---------- 읽음 처리 ----------

        @Test
        fun `scope가 SINGLE이면 알림 한 건을 읽음 처리한다`() {
            given(notificationService.read(anyLong(), anyReadRequest()))
                .willReturn(
                    NotificationReadResponse(
                        unreadCount = 4L,
                        updatedCount = 1L,
                        readAt = LocalDateTime.of(2026, 8, 7, 11, 0),
                    ),
                )

            mockMvc
                .perform(readRequest("""{ "scope": "SINGLE", "notificationId": 1 }"""))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.unreadCount").value(4))
                .andExpect(jsonPath("$.data.updatedCount").value(1))
                .andExpect(jsonPath("$.data.readAt").exists())

            val captor = ArgumentCaptor.forClass(NotificationReadRequest::class.java)
            // capture()와 다른 Matcher를 Elvis로 섞으면 Matcher가 두 번 등록되므로 기본값은 상수로 둔다.
            verify(notificationService)
                .read(anyLong(), captor.capture() ?: NotificationReadRequest(NotificationReadScope.ALL))
            assertThat(captor.value.scope).isEqualTo(NotificationReadScope.SINGLE)
            assertThat(captor.value.notificationId).isEqualTo(1L)
        }

        @Test
        fun `scope가 ALL이면 notificationId 없이도 읽음 처리한다`() {
            given(notificationService.read(anyLong(), anyReadRequest()))
                .willReturn(
                    NotificationReadResponse(
                        unreadCount = 0L,
                        updatedCount = 3L,
                        readAt = LocalDateTime.of(2026, 8, 7, 11, 0),
                    ),
                )

            mockMvc
                .perform(readRequest("""{ "scope": "ALL" }"""))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.updatedCount").value(3))
                .andExpect(jsonPath("$.data.unreadCount").value(0))
        }

        @Test
        fun `이미 모두 읽은 상태에서 전체 읽음을 호출하면 갱신 0건이고 readAt은 내려가지 않는다`() {
            given(notificationService.read(anyLong(), anyReadRequest()))
                .willReturn(NotificationReadResponse(unreadCount = 0L, updatedCount = 0L, readAt = null))

            mockMvc
                .perform(readRequest("""{ "scope": "ALL" }"""))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.updatedCount").value(0))
                .andExpect(jsonPath("$.data.readAt").doesNotExist())
        }

        @Test
        fun `scope가 SINGLE인데 notificationId가 없으면 400이다`() {
            given(notificationService.read(anyLong(), anyReadRequest())).willThrow(NotificationIdRequiredException())

            mockMvc
                .perform(readRequest("""{ "scope": "SINGLE" }"""))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("NOTIFICATION_ID_REQUIRED"))
        }

        @Test
        fun `잘못된 scope 값을 보내면 400이다`() {
            mockMvc
                .perform(readRequest("""{ "scope": "NOT_A_REAL_SCOPE" }"""))
                .andExpect(status().isBadRequest)

            verify(notificationService, never()).read(anyLong(), anyReadRequest())
        }

        @Test
        fun `다른 사용자의 알림을 읽음 처리하면 403이고 내부 정보를 노출하지 않는다`() {
            given(notificationService.read(anyLong(), anyReadRequest()))
                .willThrow(NotificationAccessDeniedException())

            mockMvc
                .perform(readRequest("""{ "scope": "SINGLE", "notificationId": 1 }"""))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("NOTIFICATION_ACCESS_DENIED"))
                .andExpect(jsonPath("$.error.message").value("본인의 알림만 접근할 수 있습니다."))
                .andExpect(jsonPath("$.error.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.error.exception").doesNotExist())
        }

        @Test
        fun `없는 알림을 읽음 처리하면 404다`() {
            given(notificationService.read(anyLong(), anyReadRequest()))
                .willThrow(NotificationNotFoundException(99L))

            mockMvc
                .perform(readRequest("""{ "scope": "SINGLE", "notificationId": 99 }"""))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("NOTIFICATION_NOT_FOUND"))
        }

        @Test
        fun `인증 없이 읽음 처리하면 401이다`() {
            mockMvc
                .perform(
                    patch("/api/v1/notifications/read")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "scope": "ALL" }"""),
                ).andExpect(status().isUnauthorized)

            verify(notificationService, never()).read(anyLong(), anyReadRequest())
        }

        // ---------- 삭제 ----------

        @Test
        fun `본인의 알림을 삭제하면 204이고 응답 본문이 없다`() {
            mockMvc
                .perform(delete("/api/v1/notifications/1").with(studentAuth()))
                .andExpect(status().isNoContent)
                .andExpect { result -> assertThat(result.response.contentAsString).isEmpty() }

            verify(notificationService).delete(memberId, 1L)
        }

        @Test
        fun `다른 사용자의 알림을 삭제하면 403이다`() {
            org.mockito.BDDMockito
                .willThrow(NotificationAccessDeniedException())
                .given(notificationService)
                .delete(memberId, 1L)

            mockMvc
                .perform(delete("/api/v1/notifications/1").with(studentAuth()))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("NOTIFICATION_ACCESS_DENIED"))
        }

        @Test
        fun `없거나 이미 삭제한 알림을 삭제하면 404다`() {
            org.mockito.BDDMockito
                .willThrow(NotificationNotFoundException(99L))
                .given(notificationService)
                .delete(memberId, 99L)

            mockMvc
                .perform(delete("/api/v1/notifications/99").with(studentAuth()))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("NOTIFICATION_NOT_FOUND"))
        }

        @Test
        fun `인증 없이 삭제하면 401이다`() {
            mockMvc
                .perform(delete("/api/v1/notifications/1"))
                .andExpect(status().isUnauthorized)

            verify(notificationService, never()).delete(anyLong(), anyLong())
        }

        private fun readRequest(body: String) =
            patch("/api/v1/notifications/read")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(studentAuth())

        // ---------- 푸시 기기 등록/갱신 ----------

        private val deviceRegisterBody =
            """{ "deviceKey": "device-key-1", "pushToken": "push-token-1", "platform": "ANDROID" }"""

        @Test
        fun `기기를 등록하면 201이고 pushToken은 응답에 없다`() {
            given(notificationDeviceService.register(anyLong(), anyDeviceRegisterRequest()))
                .willReturn(
                    NotificationDeviceResponse(
                        deviceKey = "device-key-1",
                        platform = PushPlatform.ANDROID,
                        updatedAt = LocalDateTime.of(2026, 8, 20, 10, 0),
                    ),
                )

            mockMvc
                .perform(
                    post("/api/v1/notifications/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deviceRegisterBody)
                        .with(studentAuth()),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.data.deviceKey").value("device-key-1"))
                .andExpect(jsonPath("$.data.platform").value("ANDROID"))
                .andExpect(jsonPath("$.data.pushToken").doesNotExist())
        }

        @Test
        fun `deviceKey가 비어 있으면 400이다`() {
            mockMvc
                .perform(
                    post("/api/v1/notifications/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "deviceKey": "", "pushToken": "push-token-1", "platform": "ANDROID" }""")
                        .with(studentAuth()),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))

            verify(notificationDeviceService, never()).register(anyLong(), anyDeviceRegisterRequest())
        }

        @Test
        fun `인증 없이 기기를 등록하면 401이다`() {
            mockMvc
                .perform(
                    post("/api/v1/notifications/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deviceRegisterBody),
                ).andExpect(status().isUnauthorized)

            verify(notificationDeviceService, never()).register(anyLong(), anyDeviceRegisterRequest())
        }

        // ---------- 푸시 기기 해제 ----------

        @Test
        fun `본인의 기기를 해제하면 204이고 응답 본문이 없다`() {
            mockMvc
                .perform(delete("/api/v1/notifications/devices/device-key-1").with(studentAuth()))
                .andExpect(status().isNoContent)
                .andExpect { result -> assertThat(result.response.contentAsString).isEmpty() }

            verify(notificationDeviceService).unregister(memberId, "device-key-1")
        }

        @Test
        fun `다른 회원이 등록한 기기를 해제하면 403이다`() {
            org.mockito.BDDMockito
                .willThrow(NotificationDeviceAccessDeniedException())
                .given(notificationDeviceService)
                .unregister(anyLong(), anyString())

            mockMvc
                .perform(delete("/api/v1/notifications/devices/device-key-1").with(studentAuth()))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("NOTIFICATION_DEVICE_ACCESS_DENIED"))
        }

        @Test
        fun `등록되지 않은 기기를 해제하면 404다`() {
            org.mockito.BDDMockito
                .willThrow(NotificationDeviceNotFoundException("no-such-device"))
                .given(notificationDeviceService)
                .unregister(anyLong(), anyString())

            mockMvc
                .perform(delete("/api/v1/notifications/devices/no-such-device").with(studentAuth()))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("NOTIFICATION_DEVICE_NOT_FOUND"))
        }

        @Test
        fun `인증 없이 기기를 해제하면 401이다`() {
            mockMvc
                .perform(delete("/api/v1/notifications/devices/device-key-1"))
                .andExpect(status().isUnauthorized)

            verify(notificationDeviceService, never()).unregister(anyLong(), anyString())
        }

        // ---------- 푸시 알림 설정 조회 ----------

        @Test
        fun `푸시 알림 설정을 조회할 수 있다`() {
            given(notificationSettingService.getSettings(memberId))
                .willReturn(NotificationSettingResponse(pushEnabled = true))

            mockMvc
                .perform(get("/api/v1/notifications/settings").with(studentAuth()))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.pushEnabled").value(true))
        }

        @Test
        fun `인증 없이 설정을 조회하면 401이다`() {
            mockMvc
                .perform(get("/api/v1/notifications/settings"))
                .andExpect(status().isUnauthorized)

            verify(notificationSettingService, never()).getSettings(anyLong())
        }

        // ---------- 푸시 알림 설정 변경 ----------

        @Test
        fun `푸시 알림 설정을 변경할 수 있다`() {
            given(notificationSettingService.updateSettings(anyLong(), anyBoolean()))
                .willReturn(NotificationSettingResponse(pushEnabled = false))

            mockMvc
                .perform(
                    patch("/api/v1/notifications/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "pushEnabled": false }""")
                        .with(studentAuth()),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.pushEnabled").value(false))

            verify(notificationSettingService).updateSettings(memberId, false)
        }

        @Test
        fun `인증 없이 설정을 변경하면 401이다`() {
            mockMvc
                .perform(
                    patch("/api/v1/notifications/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "pushEnabled": false }"""),
                ).andExpect(status().isUnauthorized)

            verify(notificationSettingService, never()).updateSettings(anyLong(), anyBoolean())
        }
    }
