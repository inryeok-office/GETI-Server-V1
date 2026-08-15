package team.inreok.getiserver.domain.notification.controller

import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
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
import team.inreok.getiserver.domain.notification.dto.DiscordDeliveryStatusResponse
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryStatus
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryTargetType
import team.inreok.getiserver.domain.notification.service.DiscordDeliveryService
import team.inreok.getiserver.domain.program.query.ProgramManagerQueryPort
import team.inreok.getiserver.domain.program.query.ProgramManagerSnapshot
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig
import java.time.LocalDateTime

// SecurityConfig를 명시적으로 Import해 각 경로가 실제로 요구하는 Role까지 검증한다
// (ProgramAdminControllerTest와 동일한 방식).
@WebMvcTest(controllers = [DiscordDeliveryAdminController::class])
@Import(SecurityConfig::class)
@EnableWebSecurity
class DiscordDeliveryAdminControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var discordDeliveryService: DiscordDeliveryService

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
