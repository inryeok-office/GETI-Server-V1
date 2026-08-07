package team.inreok.getiserver.domain.notification.controller

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.Pageable
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.notification.dto.NotificationListResponse
import team.inreok.getiserver.domain.notification.dto.NotificationReadAllResponse
import team.inreok.getiserver.domain.notification.dto.NotificationReadResponse
import team.inreok.getiserver.domain.notification.dto.NotificationSummaryResponse
import team.inreok.getiserver.domain.notification.dto.UnreadNotificationCountResponse
import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetType
import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetUnavailableReason
import team.inreok.getiserver.domain.notification.entity.type.NotificationType
import team.inreok.getiserver.domain.notification.exception.NotificationAccessDeniedException
import team.inreok.getiserver.domain.notification.exception.NotificationNotFoundException
import team.inreok.getiserver.domain.notification.service.NotificationService
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig
import team.inreok.getiserver.global.web.WebPageableConfig
import java.time.LocalDateTime

// SecurityConfig를 명시적으로 Import해 /api/v1/notifications 이하가 실제로 인증을 요구하는지(401)까지
// 검증한다(ProgramControllerTest와 동일한 방식). WebPageableConfig도 함께 Import해야 최대 Page
// Size(100) 강제가 이 Slice에서도 실제로 동작한다 — @WebMvcTest는 일반 @Configuration을 포함하지
// 않기 때문이다.
@WebMvcTest(controllers = [NotificationController::class])
@Import(SecurityConfig::class, WebPageableConfig::class)
@EnableWebSecurity
class NotificationControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var notificationService: NotificationService

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

        private val summary =
            NotificationSummaryResponse(
                notificationId = 1L,
                type = NotificationType.PROGRAM_PUBLISHED,
                title = "새 프로그램이 게시되었습니다",
                content = "AI 특강 모집이 시작되었습니다.",
                targetType = NotificationTargetType.PROGRAM,
                targetId = 123L,
                targetAvailable = true,
                targetUnavailableReason = null,
                deepLink = "/programs/123",
                isRead = false,
                readAt = null,
                createdAt = LocalDateTime.of(2026, 8, 7, 10, 0),
            )

        private fun listResponse(content: List<NotificationSummaryResponse> = listOf(summary)) =
            NotificationListResponse(
                content = content,
                page = 0,
                size = 20,
                totalElements = content.size.toLong(),
                totalPages = 1,
                first = true,
                last = true,
            )

        // ---------- 목록 ----------

        @Test
        fun `인증된 사용자는 자신의 알림 목록을 조회할 수 있다`() {
            given(notificationService.list(anyLong(), any(), any(), anyPageable())).willReturn(listResponse())

            mockMvc
                .perform(get("/api/v1/notifications").with(studentAuth()))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].notificationId").value(1))
                .andExpect(jsonPath("$.data.content[0].type").value("PROGRAM_PUBLISHED"))
                .andExpect(jsonPath("$.data.content[0].targetAvailable").value(true))
                .andExpect(jsonPath("$.data.content[0].deepLink").value("/programs/123"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.meta.requestId").exists())
        }

        @Test
        fun `대상이 삭제된 알림은 사유와 함께 내려간다`() {
            val deletedTarget =
                summary.copy(
                    targetAvailable = false,
                    targetUnavailableReason = NotificationTargetUnavailableReason.DELETED,
                    deepLink = null,
                )
            given(notificationService.list(anyLong(), any(), any(), anyPageable()))
                .willReturn(listResponse(listOf(deletedTarget)))

            mockMvc
                .perform(get("/api/v1/notifications").with(studentAuth()))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.content[0].targetAvailable").value(false))
                .andExpect(jsonPath("$.data.content[0].targetUnavailableReason").value("DELETED"))
                .andExpect(jsonPath("$.data.content[0].deepLink").doesNotExist())
        }

        @Test
        fun `인증 없이 알림 목록을 조회하면 401이다`() {
            mockMvc
                .perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))

            verify(notificationService, never()).list(anyLong(), any(), any(), anyPageable())
        }

        @Test
        fun `요청한 사용자 본인의 ID로만 조회한다`() {
            given(notificationService.list(anyLong(), any(), any(), anyPageable())).willReturn(listResponse())

            mockMvc
                .perform(get("/api/v1/notifications").with(studentAuth()))
                .andExpect(status().isOk)

            val memberIdCaptor = ArgumentCaptor.forClass(Long::class.javaObjectType)
            verify(notificationService).list(memberIdCaptor.capture() ?: 0L, any(), any(), anyPageable())
            assertThat(memberIdCaptor.value).isEqualTo(memberId)
        }

        @Test
        fun `읽음 여부와 종류 필터를 전달한다`() {
            given(notificationService.list(anyLong(), any(), any(), anyPageable())).willReturn(listResponse())

            mockMvc
                .perform(
                    get("/api/v1/notifications")
                        .param("isRead", "false")
                        .param("type", "PROGRAM_PUBLISHED")
                        .with(studentAuth()),
                ).andExpect(status().isOk)

            val isReadCaptor = ArgumentCaptor.forClass(Boolean::class.javaObjectType)
            val typeCaptor = ArgumentCaptor.forClass(NotificationType::class.java)
            verify(notificationService).list(anyLong(), isReadCaptor.capture(), typeCaptor.capture(), anyPageable())
            assertThat(isReadCaptor.value).isFalse
            assertThat(typeCaptor.value).isEqualTo(NotificationType.PROGRAM_PUBLISHED)
        }

        @Test
        fun `잘못된 알림 종류를 보내면 400이다`() {
            mockMvc
                .perform(get("/api/v1/notifications").param("type", "NOT_A_REAL_TYPE").with(studentAuth()))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("TYPE_MISMATCH"))
        }

        @Test
        fun `size가 최대값을 넘으면 100으로 잘린다`() {
            given(notificationService.list(anyLong(), any(), any(), anyPageable())).willReturn(listResponse())

            mockMvc
                .perform(get("/api/v1/notifications").param("size", "500").with(studentAuth()))
                .andExpect(status().isOk)

            val pageableCaptor = ArgumentCaptor.forClass(Pageable::class.java)
            verify(notificationService).list(anyLong(), any(), any(), pageableCaptor.capture() ?: Pageable.unpaged())
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

        // ---------- 단일 읽음 ----------

        @Test
        fun `단일 알림을 읽음 처리할 수 있다`() {
            val readAt = LocalDateTime.of(2026, 8, 7, 11, 0)
            given(notificationService.markAsRead(memberId, 1L))
                .willReturn(NotificationReadResponse(notificationId = 1L, isRead = true, readAt = readAt))

            mockMvc
                .perform(patch("/api/v1/notifications/1/read").with(studentAuth()))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.notificationId").value(1))
                .andExpect(jsonPath("$.data.isRead").value(true))
                .andExpect(jsonPath("$.data.readAt").exists())
        }

        @Test
        fun `다른 사용자의 알림을 읽음 처리하면 403이고 내부 정보를 노출하지 않는다`() {
            given(notificationService.markAsRead(memberId, 1L)).willThrow(NotificationAccessDeniedException())

            mockMvc
                .perform(patch("/api/v1/notifications/1/read").with(studentAuth()))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("NOTIFICATION_ACCESS_DENIED"))
                .andExpect(jsonPath("$.error.message").value("본인의 알림만 접근할 수 있습니다."))
                .andExpect(jsonPath("$.error.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.error.exception").doesNotExist())
        }

        @Test
        fun `없는 알림을 읽음 처리하면 404다`() {
            given(notificationService.markAsRead(memberId, 99L)).willThrow(NotificationNotFoundException(99L))

            mockMvc
                .perform(patch("/api/v1/notifications/99/read").with(studentAuth()))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("NOTIFICATION_NOT_FOUND"))
        }

        @Test
        fun `인증 없이 읽음 처리하면 401이다`() {
            mockMvc
                .perform(patch("/api/v1/notifications/1/read"))
                .andExpect(status().isUnauthorized)

            verify(notificationService, never()).markAsRead(anyLong(), anyLong())
        }

        // ---------- 전체 읽음 ----------

        @Test
        fun `전체 알림을 읽음 처리할 수 있다`() {
            given(notificationService.markAllAsRead(memberId))
                .willReturn(
                    NotificationReadAllResponse(
                        updatedCount = 3L,
                        readAt = LocalDateTime.of(2026, 8, 7, 11, 0),
                    ),
                )

            mockMvc
                .perform(patch("/api/v1/notifications/read-all").with(studentAuth()))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.updatedCount").value(3))
                .andExpect(jsonPath("$.data.readAt").exists())
        }

        @Test
        fun `이미 모두 읽은 상태에서 전체 읽음을 호출해도 200이다`() {
            given(notificationService.markAllAsRead(memberId))
                .willReturn(
                    NotificationReadAllResponse(
                        updatedCount = 0L,
                        readAt = LocalDateTime.of(2026, 8, 7, 11, 0),
                    ),
                )

            mockMvc
                .perform(patch("/api/v1/notifications/read-all").with(studentAuth()))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.updatedCount").value(0))
        }

        @Test
        fun `인증 없이 전체 읽음 처리하면 401이다`() {
            mockMvc
                .perform(patch("/api/v1/notifications/read-all"))
                .andExpect(status().isUnauthorized)

            verify(notificationService, never()).markAllAsRead(anyLong())
        }
    }
