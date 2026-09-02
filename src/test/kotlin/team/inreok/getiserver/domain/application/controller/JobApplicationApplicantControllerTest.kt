package team.inreok.getiserver.domain.application.controller

import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.eq
import org.mockito.BDDMockito.given
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
import team.inreok.getiserver.domain.application.dto.JobApplicationApplicantListResponse
import team.inreok.getiserver.domain.application.dto.JobApplicationApplicantResponse
import team.inreok.getiserver.domain.application.exception.ApplicationReviewForbiddenException
import team.inreok.getiserver.domain.application.exception.JobNotFoundException
import team.inreok.getiserver.domain.application.service.JobApplicationApplicantService
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.NormalSecurityTestConfig

// SecurityConfig::class를 직접 Import하지 않는다 -- Issue #162(develop 병합)가 securityFilterChain
// 자체를 개발 단계 임시 전역 permitAll로 바꿨고, 보존된 실제 Role 규칙은 NormalSecurityTestConfig가
// 제공하는 별도 Bean(SecurityConfig.normalSecurityFilterChainForTest)으로만 검증할 수 있다(다른
// 모든 Controller Test와 동일한 관례, JobApplicationAdminControllerTest 참고).
@WebMvcTest(controllers = [JobApplicationApplicantController::class])
@Import(NormalSecurityTestConfig::class)
@EnableWebSecurity
class JobApplicationApplicantControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var jobApplicationApplicantService: JobApplicationApplicantService

        @MockitoBean
        private lateinit var jwtTokenProvider: JwtTokenProvider

        private fun authOf(
            memberId: Long,
            vararg roles: String,
        ) = authentication(
            UsernamePasswordAuthenticationToken(memberId, null, roles.map { SimpleGrantedAuthority("ROLE_$it") }),
        )

        // Pageable Non-null Parameter에 bare any()를 쓰면 null 반환으로 NPE가 나므로 Elvis로
        // Fallback을 둔다(JobApplicationAdminControllerTest.anyPageable과 동일한 관례).
        private fun anyPageable(): Pageable = any(Pageable::class.java) ?: Pageable.unpaged()

        @Test
        fun `담당 교사는 지원자 목록을 조회한다`() {
            val response =
                JobApplicationApplicantListResponse(
                    content =
                        listOf(
                            JobApplicationApplicantResponse(
                                applicationId = 1L,
                                memberId = 7L,
                                name = "홍길동",
                                profileImageUrl = "https://cdn.example.com/50",
                                cohort = 10,
                                department = "SW_DEVELOPMENT",
                            ),
                        ),
                    page = 0,
                    size = 20,
                    totalElements = 1,
                    totalPages = 1,
                    first = true,
                    last = true,
                )
            given(jobApplicationApplicantService.list(eq(1L), eq(100L), eq(false), anyPageable())).willReturn(response)

            mockMvc
                .perform(get("/api/v1/admin/jobs/1/applicants").with(authOf(100L, "TEACHER")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.content[0].applicationId").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("홍길동"))
        }

        @Test
        fun `담당자가 아닌 교사는 403이다`() {
            given(jobApplicationApplicantService.list(eq(1L), eq(999L), eq(false), anyPageable()))
                .willThrow(ApplicationReviewForbiddenException())

            mockMvc
                .perform(get("/api/v1/admin/jobs/1/applicants").with(authOf(999L, "TEACHER")))
                .andExpect(status().isForbidden)
        }

        @Test
        fun `존재하지 않는 공고는 404다`() {
            given(jobApplicationApplicantService.list(anyLong(), anyLong(), anyBoolean(), anyPageable()))
                .willThrow(JobNotFoundException(999L))

            mockMvc
                .perform(get("/api/v1/admin/jobs/999/applicants").with(authOf(100L, "TEACHER")))
                .andExpect(status().isNotFound)
        }

        @Test
        fun `인증 없이 호출하면 401이다`() {
            mockMvc
                .perform(get("/api/v1/admin/jobs/1/applicants"))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `학생 Role로 호출하면 403이다`() {
            mockMvc
                .perform(get("/api/v1/admin/jobs/1/applicants").with(authOf(1L, "STUDENT")))
                .andExpect(status().isForbidden)
        }
    }
