package team.inreok.getiserver.domain.notification.controller

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.notification.dto.DiscordDeliveryListItemResponse
import team.inreok.getiserver.domain.notification.dto.DiscordDeliveryListResponse
import team.inreok.getiserver.domain.notification.dto.DiscordDeliveryStatusResponse
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryAction
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryStatus
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryTargetType
import team.inreok.getiserver.domain.notification.service.DiscordDeliveryAdminQueryService
import team.inreok.getiserver.domain.notification.service.DiscordDeliveryService
import team.inreok.getiserver.domain.program.query.ProgramManagerQueryPort
import team.inreok.getiserver.domain.program.query.ProgramManagerSnapshot
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig
import team.inreok.getiserver.global.web.WebPageableConfig
import java.time.LocalDateTime

// SecurityConfig를 명시적으로 Import해 각 경로가 실제로 요구하는 Role까지 검증한다
// (ProgramAdminControllerTest와 동일한 방식). WebPageableConfig도 함께 Import해야 목록 조회의
// 최대 Page Size(100) 강제가 이 Slice에서도 실제로 동작한다 -- @WebMvcTest는 일반 @Configuration을
// 포함하지 않는다(NotificationControllerTest와 같은 이유).
@WebMvcTest(controllers = [DiscordDeliveryAdminController::class])
@Import(team.inreok.getiserver.global.security.NormalSecurityTestConfig::class, WebPageableConfig::class)
@EnableWebSecurity
class DiscordDeliveryAdminControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var discordDeliveryService: DiscordDeliveryService

        @MockitoBean
        private lateinit var discordDeliveryAdminQueryService: DiscordDeliveryAdminQueryService

        @MockitoBean
        private lateinit var programManagerQueryPort: ProgramManagerQueryPort

        @MockitoBean
        private lateinit var jwtTokenProvider: JwtTokenProvider

        private fun authOf(
            memberId: Long,
            vararg roles: String,
        ) = authentication(
            UsernamePasswordAuthenticationToken(
                memberId,
                null,
                roles.map { SimpleGrantedAuthority("ROLE_$it") },
            ),
        )

        private fun statusResponse(targetType: DiscordDeliveryTargetType) =
            DiscordDeliveryStatusResponse(
                targetType = targetType,
                targetId = 1L,
                channelId = "channel-1",
                messageId = "message-1",
                status = DiscordDeliveryStatus.DELIVERED,
                automaticRetryCount = 0,
                manualRetryCount = 0,
                maxAutomaticRetryCount = 3,
                maxManualRetryCount = 3,
                canRetry = false,
                failureCode = null,
                failureReason = null,
                requestedAt = LocalDateTime.of(2026, 8, 1, 9, 0),
                lastSyncedAt = LocalDateTime.of(2026, 8, 1, 9, 1),
            )

        private fun listItem(targetType: DiscordDeliveryTargetType = DiscordDeliveryTargetType.JOB) =
            DiscordDeliveryListItemResponse(
                deliveryId = 42L,
                targetType = targetType,
                targetId = 123L,
                targetName = "2026 상반기 신입 백엔드 개발자",
                action = DiscordDeliveryAction.CREATE,
                channelId = "channel-1",
                messageId = null,
                status = DiscordDeliveryStatus.FAILED,
                automaticRetryCount = 3,
                manualRetryCount = 0,
                maxAutomaticRetryCount = 3,
                maxManualRetryCount = 3,
                canRetry = true,
                failureCode = "RATE_LIMITED",
                failureReason = "Discord 요청이 일시적으로 제한되었습니다.",
                requestedAt = LocalDateTime.of(2026, 8, 20, 9, 0),
                lastSyncedAt = LocalDateTime.of(2026, 8, 20, 9, 12),
            )

        private fun listResponse(content: List<DiscordDeliveryListItemResponse> = listOf(listItem())) =
            DiscordDeliveryListResponse(
                content = content,
                page = 0,
                size = 20,
                totalElements = content.size.toLong(),
                totalPages = 1,
                first = true,
                last = true,
            )

        private fun anyPageable(): Pageable = any(Pageable::class.java) ?: Pageable.unpaged()

        // --- 횡단 목록: 개발자 전용 ------------------------------------------------

        @Test
        fun `개발자는 Discord 전달 내역 전체 목록을 조회할 수 있다`() {
            given(discordDeliveryAdminQueryService.listRecent(any(), anyPageable())).willReturn(listResponse())

            mockMvc
                .perform(get("/api/v1/admin/discord-deliveries").with(authOf(1L, "DEVELOPER")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.content[0].deliveryId").value(42))
                .andExpect(jsonPath("$.data.content[0].targetType").value("JOB"))
                .andExpect(jsonPath("$.data.content[0].targetId").value(123))
                .andExpect(jsonPath("$.data.content[0].targetName").value("2026 상반기 신입 백엔드 개발자"))
                .andExpect(jsonPath("$.data.content[0].action").value("CREATE"))
                .andExpect(jsonPath("$.data.content[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data.content[0].canRetry").value(true))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(1))
        }

        @Test
        fun `목록 응답에는 메시지 본문이 담기지 않는다`() {
            // Payload Snapshot을 저장하지 않고(요구사항 §19·§39) 문의 본문은 §40이 노출을 금지하므로,
            // Client Mock의 messageBody에 대응하는 Field가 응답에 생기지 않아야 한다.
            given(discordDeliveryAdminQueryService.listRecent(any(), anyPageable())).willReturn(listResponse())

            mockMvc
                .perform(get("/api/v1/admin/discord-deliveries").with(authOf(1L, "DEVELOPER")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.content[0].messageBody").doesNotExist())
        }

        @Test
        fun `교사는 Discord 전달 내역 전체 목록을 조회할 수 없고 403을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/admin/discord-deliveries").with(authOf(1L, "TEACHER")))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
        }

        @Test
        fun `인증 없이 Discord 전달 내역 전체 목록을 조회하면 401을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/admin/discord-deliveries"))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `status Filter는 그대로 Service에 전달된다`() {
            given(discordDeliveryAdminQueryService.listRecent(any(), anyPageable())).willReturn(listResponse())

            mockMvc
                .perform(
                    get("/api/v1/admin/discord-deliveries")
                        .param("status", "FAILED")
                        .with(authOf(1L, "DEVELOPER")),
                ).andExpect(status().isOk)

            val statusCaptor = ArgumentCaptor.forClass(DiscordDeliveryStatus::class.java)
            verify(discordDeliveryAdminQueryService).listRecent(statusCaptor.capture(), anyPageable())
            assertThat(statusCaptor.value).isEqualTo(DiscordDeliveryStatus.FAILED)
        }

        @Test
        fun `status를 생략하면 null로 전달되어 전체를 조회한다`() {
            given(discordDeliveryAdminQueryService.listRecent(any(), anyPageable())).willReturn(listResponse())

            mockMvc
                .perform(get("/api/v1/admin/discord-deliveries").with(authOf(1L, "DEVELOPER")))
                .andExpect(status().isOk)

            val statusCaptor = ArgumentCaptor.forClass(DiscordDeliveryStatus::class.java)
            verify(discordDeliveryAdminQueryService).listRecent(statusCaptor.capture(), anyPageable())
            assertThat(statusCaptor.value).isNull()
        }

        @Test
        fun `status 값이 올바르지 않으면 400을 반환한다`() {
            mockMvc
                .perform(
                    get("/api/v1/admin/discord-deliveries")
                        .param("status", "UNKNOWN")
                        .with(authOf(1L, "DEVELOPER")),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("TYPE_MISMATCH"))
        }

        @Test
        fun `목록 size가 최대값을 넘으면 100으로 잘린다`() {
            given(discordDeliveryAdminQueryService.listRecent(any(), anyPageable())).willReturn(listResponse())

            mockMvc
                .perform(
                    get("/api/v1/admin/discord-deliveries")
                        .param("size", "500")
                        .with(authOf(1L, "DEVELOPER")),
                ).andExpect(status().isOk)

            val pageableCaptor = ArgumentCaptor.forClass(Pageable::class.java)
            verify(discordDeliveryAdminQueryService).listRecent(
                any(),
                pageableCaptor.capture() ?: Pageable.unpaged(),
            )
            assertThat(pageableCaptor.value.pageSize).isEqualTo(100)
        }

        // --- Job: Role만 검사 --------------------------------------------------

        @Test
        fun `교사는 공고 Discord 상태를 조회할 수 있다`() {
            given(discordDeliveryService.findStatus(DiscordDeliveryTargetType.JOB, 1L))
                .willReturn(statusResponse(DiscordDeliveryTargetType.JOB))

            mockMvc
                .perform(get("/api/v1/admin/jobs/1/discord").with(authOf(1L, "TEACHER")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.targetType").value("JOB"))
        }

        @Test
        fun `학생은 공고 Discord 상태를 조회할 수 없고 403을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/admin/jobs/1/discord").with(authOf(1L, "STUDENT")))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
        }

        @Test
        fun `인증 없이 공고 Discord 상태를 조회하면 401을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/admin/jobs/1/discord"))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `개발자는 공고 Discord 전달을 재시도할 수 있다`() {
            given(discordDeliveryService.retryManuallyForTarget(DiscordDeliveryTargetType.JOB, 1L))
                .willReturn(statusResponse(DiscordDeliveryTargetType.JOB).copy(status = DiscordDeliveryStatus.PENDING))

            mockMvc
                .perform(post("/api/v1/admin/jobs/1/discord/retry").with(authOf(1L, "DEVELOPER")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.status").value("PENDING"))
        }

        // --- Program: 등록자·담당 교사·개발자만 -----------------------------------

        @Test
        fun `등록자는 프로그램 Discord 상태를 조회할 수 있다`() {
            given(programManagerQueryPort.findById(1L))
                .willReturn(ProgramManagerSnapshot(programId = 1L, createdByMemberId = 7L, managerMemberId = null))
            given(discordDeliveryService.findStatus(DiscordDeliveryTargetType.PROGRAM, 1L))
                .willReturn(statusResponse(DiscordDeliveryTargetType.PROGRAM))

            mockMvc
                .perform(get("/api/v1/admin/programs/1/discord").with(authOf(7L, "TEACHER")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.targetType").value("PROGRAM"))
        }

        @Test
        fun `등록자도 담당 교사도 아닌 교사는 403을 반환한다`() {
            given(programManagerQueryPort.findById(1L))
                .willReturn(ProgramManagerSnapshot(programId = 1L, createdByMemberId = 7L, managerMemberId = 8L))

            mockMvc
                .perform(get("/api/v1/admin/programs/1/discord").with(authOf(99L, "TEACHER")))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("DISCORD_DELIVERY_MANAGE_FORBIDDEN"))
        }

        @Test
        fun `개발자는 등록자가 아니어도 프로그램 Discord 상태를 조회할 수 있다`() {
            given(programManagerQueryPort.findById(1L))
                .willReturn(ProgramManagerSnapshot(programId = 1L, createdByMemberId = 7L, managerMemberId = null))
            given(discordDeliveryService.findStatus(DiscordDeliveryTargetType.PROGRAM, 1L))
                .willReturn(statusResponse(DiscordDeliveryTargetType.PROGRAM))

            mockMvc
                .perform(get("/api/v1/admin/programs/1/discord").with(authOf(99L, "DEVELOPER")))
                .andExpect(status().isOk)
        }

        @Test
        fun `존재하지 않는 프로그램의 Discord 상태를 조회하면 404를 반환한다`() {
            given(programManagerQueryPort.findById(anyLong())).willReturn(null)

            mockMvc
                .perform(get("/api/v1/admin/programs/999/discord").with(authOf(1L, "TEACHER")))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("DISCORD_DELIVERY_NOT_FOUND"))
        }

        @Test
        fun `담당 교사는 프로그램 Discord 전달을 재시도할 수 있다`() {
            given(programManagerQueryPort.findById(1L))
                .willReturn(ProgramManagerSnapshot(programId = 1L, createdByMemberId = 7L, managerMemberId = 8L))
            given(discordDeliveryService.retryManuallyForTarget(DiscordDeliveryTargetType.PROGRAM, 1L))
                .willReturn(
                    statusResponse(DiscordDeliveryTargetType.PROGRAM).copy(status = DiscordDeliveryStatus.PENDING),
                )

            mockMvc
                .perform(post("/api/v1/admin/programs/1/discord/retry").with(authOf(8L, "TEACHER")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.status").value("PENDING"))
        }

        @Test
        fun `학생은 프로그램 Discord 전달을 재시도할 수 없고 403을 반환한다`() {
            // SecurityConfig가 /api/v1/admin/programs/** 자체를 TEACHER 또는 DEVELOPER로 제한하므로
            // Controller의 소유권 검증에 도달하기 전에 이미 거부된다.
            mockMvc
                .perform(post("/api/v1/admin/programs/1/discord/retry").with(authOf(1L, "STUDENT")))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
        }

        // --- Inquiry: 개발자만, 조회만 제공 --------------------------------------

        @Test
        fun `개발자는 문의 Discord 상태를 조회할 수 있다`() {
            given(discordDeliveryService.findStatus(DiscordDeliveryTargetType.INQUIRY, 1L))
                .willReturn(statusResponse(DiscordDeliveryTargetType.INQUIRY))

            mockMvc
                .perform(get("/api/v1/admin/inquiries/1/discord").with(authOf(1L, "DEVELOPER")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.targetType").value("INQUIRY"))
        }

        @Test
        fun `교사는 문의 Discord 상태를 조회할 수 없고 403을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/admin/inquiries/1/discord").with(authOf(1L, "TEACHER")))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
        }
    }
