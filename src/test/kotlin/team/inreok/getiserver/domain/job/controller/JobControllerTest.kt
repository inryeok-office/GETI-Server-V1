package team.inreok.getiserver.domain.job.controller

import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
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
import team.inreok.getiserver.domain.company.query.CompanySummary
import team.inreok.getiserver.domain.job.dto.JobDetailResponse
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.entity.type.PostingType
import team.inreok.getiserver.domain.job.exception.JobNotFoundException
import team.inreok.getiserver.domain.job.exception.JobNotVisibleException
import team.inreok.getiserver.domain.job.service.JobService
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig
import java.time.LocalDateTime

// SecurityConfig를 명시적으로 Import해 /api/v1/jobs가 실제로 인증을 요구하는지(401)까지 검증한다.
// 목록/검색(GET /api/v1/jobs)은 Issue #69에서 domain.search.controller.JobSearchController로
// 옮겨졌다 — 이 Class는 상세 조회만 다룬다(JobController.kt의 Class 주석 참고).
@WebMvcTest(controllers = [JobController::class])
@Import(SecurityConfig::class)
@EnableWebSecurity
class JobControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var jobService: JobService

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

        // --- 상세 ---

        @Test
        fun `공개 상세를 조회하면 200과 증가된 조회수를 반환한다`() {
            given(jobService.getPublicDetail(1L)).willReturn(detailResponse(viewCount = 11))

            mockMvc
                .perform(get("/api/v1/jobs/1").with(authOf(1L, "STUDENT")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobId").value(1))
                .andExpect(jsonPath("$.data.viewCount").value(11))
                .andExpect(jsonPath("$.data.company.companyId").value(1))
        }

        @Test
        fun `없거나 삭제된 공고를 조회하면 404를 반환한다`() {
            willThrow(JobNotFoundException(1L)).given(jobService).getPublicDetail(anyLong())

            mockMvc
                .perform(get("/api/v1/jobs/1").with(authOf(1L, "STUDENT")))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("JOB_NOT_FOUND"))
        }

        @Test
        fun `공개되지 않은 공고를 조회하면 403을 반환한다`() {
            willThrow(JobNotVisibleException(1L)).given(jobService).getPublicDetail(anyLong())

            mockMvc
                .perform(get("/api/v1/jobs/1").with(authOf(1L, "STUDENT")))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("JOB_NOT_VISIBLE"))
        }

        @Test
        fun `인증 없이 상세를 조회하면 401을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/jobs/1"))
                .andExpect(status().isUnauthorized)
        }

        // --- Fixture ---

        private fun detailResponse(viewCount: Long) =
            JobDetailResponse(
                jobId = 1L,
                title = "2026 상반기 백엔드 채용",
                postingType = PostingType.MOU,
                applicationMethod = ApplicationMethod.EXTERNAL,
                status = JobStatus.PUBLISHED,
                company = CompanySummary(1L, "인력개발원"),
                content = "## 모집 부문",
                externalUrl = "https://example.com/apply",
                startDate = null,
                endDate = null,
                targetGrade = 3,
                capacity = 2,
                firstComeServed = false,
                viewCount = viewCount,
                publishedAt = LocalDateTime.of(2026, 7, 25, 9, 0),
                closedAt = null,
                createdAt = LocalDateTime.of(2026, 7, 20, 10, 0),
                updatedAt = LocalDateTime.of(2026, 7, 25, 9, 0),
            )
    }
