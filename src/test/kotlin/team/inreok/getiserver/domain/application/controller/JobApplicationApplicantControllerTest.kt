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
import org.springframework.test.annotation.DirtiesContext
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
import team.inreok.getiserver.global.security.SecurityConfig

// CI(Linux)에서만 재현되고 로컬(Windows, 단독/전체 Suite 5회 이상)에서는 재현되지 않는
// "인증 없이 호출하면 401이다"/"학생 Role로 호출하면 403이다" 실패를 조사했다 -- 두 Case 모두
// AuthorizationFilter는 실제로 실행됐지만(Servlet Filter Chain Stack Trace로 확인) 요청을
// Controller까지 통과시켰다. 즉 이 Class의 SecurityFilterChain이 다른 WebMvcTest Class와 공유된
// ApplicationContext Cache에서 의도와 다르게 재사용됐을 가능성이 가장 높다(Spring TestContext
// Cache 상호작용, GETI-Server가 사용하는 Spring Boot 4.1 Preview 계열 Test Slice에서 관찰됨).
// 이 Class가 시작하기 전에 Cache된 Context를 강제로 폐기하고 새로 만들어, 다른 Test와 Context를
// 공유하지 않도록 원인을 격리한다.
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@WebMvcTest(controllers = [JobApplicationApplicantController::class])
@Import(SecurityConfig::class)
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
