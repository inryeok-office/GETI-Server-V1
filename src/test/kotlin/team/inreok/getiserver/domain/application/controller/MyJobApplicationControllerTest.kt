package team.inreok.getiserver.domain.application.controller

import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
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
import team.inreok.getiserver.domain.application.dto.MyJobApplicationJobSummary
import team.inreok.getiserver.domain.application.dto.MyJobApplicationListItemResponse
import team.inreok.getiserver.domain.application.dto.MyJobApplicationListResponse
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus
import team.inreok.getiserver.domain.application.service.JobApplicationService
import team.inreok.getiserver.domain.company.query.CompanySummary
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.entity.type.PostingType
import team.inreok.getiserver.global.security.JwtTokenProvider
import java.time.LocalDateTime

@WebMvcTest(controllers = [MyJobApplicationController::class])
@Import(team.inreok.getiserver.global.security.NormalSecurityTestConfig::class)
@EnableWebSecurity
class MyJobApplicationControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var jobApplicationService: JobApplicationService

        @MockitoBean
        private lateinit var jwtTokenProvider: JwtTokenProvider

        private fun authOf(
            memberId: Long,
            vararg roles: String,
        ) = authentication(
            UsernamePasswordAuthenticationToken(memberId, null, roles.map { SimpleGrantedAuthority("ROLE_$it") }),
        )

        private val fixedTime = LocalDateTime.of(2026, 3, 1, 10, 0, 0)

        // Kotlin에서 Mockito의 any()는 null을 반환해 non-null로 추론되는 Object 타입 인자(Pageable)에
        // 쓰면 NullPointerException이 난다(JobApplicationServiceImplTest.anyJobApplication()과 같은 이유).
        private fun anyPageable(): Pageable = any(Pageable::class.java) ?: PageRequest.of(0, 20)

        private fun listResponse() =
            MyJobApplicationListResponse(
                content =
                    listOf(
                        MyJobApplicationListItemResponse(
                            applicationId = 1L,
                            job =
                                MyJobApplicationJobSummary(
                                    jobId = 1L,
                                    title = "인턴 채용",
                                    postingType = PostingType.MOU,
                                    applicationMethod = ApplicationMethod.INTERNAL,
                                    status = JobStatus.PUBLISHED,
                                    company = CompanySummary(companyId = 1L, name = "인력개발원"),
                                    endDate = null,
                                    viewCount = 10,
                                    bookmarked = false,
                                ),
                            status = JobApplicationStatus.SUBMITTED,
                            submittedAt = fixedTime,
                            updatedAt = fixedTime,
                        ),
                    ),
                page = 0,
                size = 20,
                totalElements = 1,
                totalPages = 1,
                first = true,
                last = true,
            )

        @Test
        fun `본인 지원 목록을 조회하면 200과 함께 목록을 반환한다`() {
            given(jobApplicationService.list(anyLong(), isNull(), anyPageable()))
                .willReturn(listResponse())

            mockMvc
                .perform(get("/api/v1/me/job-applications").with(authOf(1L, "STUDENT")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.content[0].applicationId").value(1))
                .andExpect(jsonPath("$.data.content[0].job.title").value("인턴 채용"))
                .andExpect(jsonPath("$.data.content[0].status").value("SUBMITTED"))
        }

        @Test
        fun `status 필터를 지정하면 그대로 전달한다`() {
            given(
                jobApplicationService.list(
                    eq(1L),
                    eq(JobApplicationStatus.SUBMITTED),
                    anyPageable(),
                ),
            ).willReturn(listResponse())

            mockMvc
                .perform(get("/api/v1/me/job-applications?status=SUBMITTED").with(authOf(1L, "STUDENT")))
                .andExpect(status().isOk)
        }

        @Test
        fun `인증 없이 목록을 요청하면 401을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/me/job-applications"))
                .andExpect(status().isUnauthorized)
        }
    }
