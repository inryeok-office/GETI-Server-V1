package team.inreok.getiserver.domain.search.controller

import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.search.entity.SearchReindexRun
import team.inreok.getiserver.domain.search.entity.type.SearchReindexStatus
import team.inreok.getiserver.domain.search.exception.ReindexAlreadyRunningException
import team.inreok.getiserver.domain.search.reindex.SearchReindexService
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig

// SecurityConfig를 명시적으로 Import해 /api/v1/admin/search-actions가 실제로 DEVELOPER 권한을
// 요구하는지(401/403)까지 검증한다(CollectorAdminControllerTest와 동일한 방식).
@WebMvcTest(controllers = [SearchAdminController::class])
@Import(team.inreok.getiserver.global.security.NormalSecurityTestConfig::class)
@EnableWebSecurity
class SearchAdminControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var searchReindexService: SearchReindexService

        @MockitoBean
        private lateinit var jwtTokenProvider: JwtTokenProvider

        private fun authOf(
            memberId: Long,
            vararg roles: String,
        ) = authentication(
            UsernamePasswordAuthenticationToken(memberId, null, roles.map { SimpleGrantedAuthority("ROLE_$it") }),
        )

        private fun runOf(status: SearchReindexStatus = SearchReindexStatus.PENDING) =
            SearchReindexRun().apply {
                id = 1L
                this.status = status
            }

        @Test
        fun `개발자가 재색인을 요청하면 202와 접수 상태를 반환한다`() {
            given(searchReindexService.triggerReindex()).willReturn(runOf())

            mockMvc
                .perform(
                    post("/api/v1/admin/search-actions")
                        .with(authOf(1L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "action": "REINDEX" }"""),
                ).andExpect(status().isAccepted)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.operationId").value(1))
                .andExpect(jsonPath("$.data.action").value("REINDEX"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
        }

        @Test
        fun `이미 재색인이 진행 중이면 409를 반환한다`() {
            willThrow(ReindexAlreadyRunningException()).given(searchReindexService).triggerReindex()

            mockMvc
                .perform(
                    post("/api/v1/admin/search-actions")
                        .with(authOf(1L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "action": "REINDEX" }"""),
                ).andExpect(status().isConflict)
                .andExpect(jsonPath("$.error.code").value("REINDEX_ALREADY_RUNNING"))
        }

        @Test
        fun `action이 없으면 400을 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/admin/search-actions")
                        .with(authOf(1L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{}"""),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `지원하지 않는 action 값을 보내면 400을 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/admin/search-actions")
                        .with(authOf(1L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "action": "REBUILD_EVERYTHING" }"""),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `학생은 재색인을 요청할 수 없고 403을 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/admin/search-actions")
                        .with(authOf(1L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "action": "REINDEX" }"""),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
        }

        @Test
        fun `교사는 재색인을 요청할 수 없고 403을 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/admin/search-actions")
                        .with(authOf(1L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "action": "REINDEX" }"""),
                ).andExpect(status().isForbidden)
        }

        @Test
        fun `인증 없이 요청하면 401을 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/admin/search-actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "action": "REINDEX" }"""),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        }
    }
