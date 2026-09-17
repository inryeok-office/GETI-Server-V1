package team.inreok.getiserver.domain.notification.controller

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.notification.dto.DiscordSendTargetListResponse
import team.inreok.getiserver.domain.notification.entity.type.DiscordSendTargetType
import team.inreok.getiserver.domain.notification.service.DiscordSendTargetQueryService
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.web.WebPageableConfig

@WebMvcTest(controllers = [DiscordSendTargetAdminController::class])
@Import(team.inreok.getiserver.global.security.NormalSecurityTestConfig::class, WebPageableConfig::class)
@EnableWebSecurity
class DiscordSendTargetAdminControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var queryService: DiscordSendTargetQueryService

        @MockitoBean
        private lateinit var jwtTokenProvider: JwtTokenProvider

        @Test
        fun `DEVELOPER는 Discord 발송 대상 목록을 조회할 수 있다`() {
            given(queryService.list(any(), any(), any(), anyPageable())).willReturn(emptyResponse())

            mockMvc
                .perform(get("/api/v1/admin/discord-send-targets").with(authOf("DEVELOPER")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.content").isArray)

            val captor = ArgumentCaptor.forClass(DiscordSendTargetType::class.java)
            verify(queryService).list(captor.capture(), any(), any(), anyPageable())
            assertThat(captor.value).isNull()
        }

        @Test
        fun `targetType targetName targetGrade를 Service에 전달한다`() {
            given(queryService.list(any(), any(), any(), anyPageable())).willReturn(emptyResponse())

            mockMvc
                .perform(
                    get("/api/v1/admin/discord-send-targets")
                        .param("targetType", "PROGRAM")
                        .param("targetName", "AI")
                        .param("targetGrade", "2")
                        .param("page", "1")
                        .param("size", "50")
                        .with(authOf("DEVELOPER")),
                ).andExpect(status().isOk)

            verify(queryService).list(
                eq(DiscordSendTargetType.PROGRAM),
                eq("AI"),
                eq(2),
                anyPageable(),
            )
        }

        @Test
        fun `다른 Role은 403이다`() {
            mockMvc
                .perform(get("/api/v1/admin/discord-send-targets").with(authOf("TEACHER")))
                .andExpect(status().isForbidden)
        }

        @Test
        fun `미인증 요청은 401이다`() {
            mockMvc
                .perform(get("/api/v1/admin/discord-send-targets"))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `Inquiry targetType은 지원하지 않아 400이다`() {
            mockMvc
                .perform(
                    get("/api/v1/admin/discord-send-targets")
                        .param("targetType", "INQUIRY")
                        .with(authOf("DEVELOPER")),
                ).andExpect(status().isBadRequest)
        }

        private fun authOf(role: String) =
            authentication(
                UsernamePasswordAuthenticationToken(
                    1L,
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_$role")),
                ),
            )

        private fun anyPageable(): Pageable = any(Pageable::class.java) ?: Pageable.unpaged()

        private fun emptyResponse() =
            DiscordSendTargetListResponse(
                content = emptyList(),
                page = 0,
                size = 20,
                totalElements = 0,
                totalPages = 0,
                first = true,
                last = true,
            )
    }
