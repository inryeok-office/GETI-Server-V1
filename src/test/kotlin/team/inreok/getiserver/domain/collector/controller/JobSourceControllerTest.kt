package team.inreok.getiserver.domain.collector.controller

import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyBoolean
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.collector.dto.PublicJobSourceListResponse
import team.inreok.getiserver.domain.collector.dto.PublicJobSourceResponse
import team.inreok.getiserver.domain.collector.entity.type.JobSourceType
import team.inreok.getiserver.domain.collector.service.JobSourceService
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig

// SecurityConfig를 명시적으로 Import해 /api/v1/job-sources가 인증만 요구하는지(401) 검증한다.
@WebMvcTest(controllers = [JobSourceController::class])
@Import(team.inreok.getiserver.global.security.NormalSecurityTestConfig::class)
@EnableWebSecurity
class JobSourceControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var jobSourceService: JobSourceService

        @MockitoBean
        private lateinit var jwtTokenProvider: JwtTokenProvider

        private fun authOf(
            memberId: Long,
            vararg roles: String,
        ) = authentication(
            UsernamePasswordAuthenticationToken(memberId, null, roles.map { SimpleGrantedAuthority("ROLE_$it") }),
        )

        @Test
        fun `학생도 공개 공고 출처 목록을 조회할 수 있다`() {
            given(jobSourceService.listPublic(anyBoolean())).willReturn(
                PublicJobSourceListResponse(
                    sources =
                        listOf(
                            PublicJobSourceResponse(
                                sourceId = 1L,
                                name = "병역일터",
                                sourceType = JobSourceType.EXTERNAL_API,
                                active = false,
                            ),
                        ),
                ),
            )

            mockMvc
                .perform(get("/api/v1/job-sources").with(authOf(1L, "STUDENT")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.sources[0].name").value("병역일터"))
        }

        @Test
        fun `인증 없이 조회하면 401을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/job-sources"))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        }
    }
