package team.inreok.getiserver.domain.search.controller

import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.eq
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
import team.inreok.getiserver.domain.ai.entity.type.AiDifficulty
import team.inreok.getiserver.domain.ai.entity.type.AiFitLevel
import team.inreok.getiserver.domain.company.query.CompanySummary
import team.inreok.getiserver.domain.job.access.JobApplicationEligibilityAccessSnapshot
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.entity.type.PostingType
import team.inreok.getiserver.domain.search.dto.JobSearchResponse
import team.inreok.getiserver.domain.search.dto.JobSort
import team.inreok.getiserver.domain.search.dto.JobSummaryResponse
import team.inreok.getiserver.domain.search.service.JobSearchService
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig
import java.time.LocalDateTime

// SecurityConfig를 명시적으로 Import해 /api/v1/jobs가 실제로 인증을 요구하는지(401)까지 검증한다
// (JobControllerTest와 동일한 방식, Issue #69로 목록/검색만 이 Controller로 옮겨졌다).
@WebMvcTest(controllers = [JobSearchController::class])
@Import(team.inreok.getiserver.global.security.NormalSecurityTestConfig::class)
@EnableWebSecurity
class JobSearchControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var jobSearchService: JobSearchService

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

        @Test
        fun `공개 목록을 조회하면 200과 Member·Company와 동일한 목록 응답을 반환한다`() {
            given(
                jobSearchService.search(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    anyBoolean(),
                    anySort(),
                    any(),
                    anyPageable(),
                    anyLong(),
                ),
            ).willReturn(searchResponse())

            mockMvc
                .perform(get("/api/v1/jobs").with(authOf(1L, "STUDENT")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].jobId").value(1))
                .andExpect(jsonPath("$.data.content[0].company.name").value("인력개발원"))
                .andExpect(jsonPath("$.data.content[0].location").value("서울특별시 중구"))
                .andExpect(jsonPath("$.data.content[0].employmentType").value("인턴"))
                .andExpect(jsonPath("$.data.content[0].bookmarked").value(true))
                .andExpect(
                    jsonPath("$.data.content[0].company.logoUrl")
                        .value("https://storage.example/company-logo?signature=test"),
                ).andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.first").value(true))
                .andExpect(jsonPath("$.data.last").value(true))
        }

        @Test
        fun `교사와 개발자도 공개 목록을 조회할 수 있다`() {
            given(
                jobSearchService.search(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    anyBoolean(),
                    anySort(),
                    any(),
                    anyPageable(),
                    anyLong(),
                ),
            ).willReturn(searchResponse())

            mockMvc.perform(get("/api/v1/jobs").with(authOf(1L, "TEACHER"))).andExpect(status().isOk)
            mockMvc.perform(get("/api/v1/jobs").with(authOf(1L, "DEVELOPER"))).andExpect(status().isOk)
        }

        @Test
        fun `size 0과 100은 허용되고 101은 서버가 최대값으로 강제한다`() {
            given(
                jobSearchService.search(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    anyBoolean(),
                    anySort(),
                    any(),
                    anyPageable(),
                    anyLong(),
                ),
            ).willReturn(searchResponse())

            mockMvc.perform(get("/api/v1/jobs").param("size", "0").with(authOf(1L, "STUDENT"))).andExpect(status().isOk)
            mockMvc
                .perform(
                    get("/api/v1/jobs").param("size", "100").with(authOf(1L, "STUDENT")),
                ).andExpect(status().isOk)
            // WebPageableConfig가 최대 size를 100으로 강제한다(전역 설정, 다른 목록 API와 공유).
            mockMvc
                .perform(
                    get("/api/v1/jobs").param("size", "101").with(authOf(1L, "STUDENT")),
                ).andExpect(status().isOk)
        }

        @Test
        fun `지원하지 않는 정렬 방향을 보내면 400을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/jobs").param("direction", "BOGUS").with(authOf(1L, "STUDENT")))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("TYPE_MISMATCH"))
        }

        @Test
        fun `인증 없이 목록을 조회하면 401을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/jobs"))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        }

        @Test
        fun `공개 목록에서 조회할 수 없는 상태를 필터로 보내면 400을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/jobs").param("status", "DRAFT").with(authOf(1L, "STUDENT")))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("TYPE_MISMATCH"))
        }

        @Test
        fun `지원하지 않는 정렬을 보내면 400을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/jobs").param("sort", "BOGUS").with(authOf(1L, "STUDENT")))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("TYPE_MISMATCH"))
        }

        @Test
        fun `지원하지 않는 기업 유형을 필터로 보내면 400을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/jobs").param("companyType", "BOGUS").with(authOf(1L, "STUDENT")))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("TYPE_MISMATCH"))
        }

        @Test
        fun `지원하지 않는 AI 분석 필터 값을 보내면 400을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/jobs").param("difficulty", "IMPOSSIBLE").with(authOf(1L, "STUDENT")))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("TYPE_MISMATCH"))
        }

        @Test
        fun `AI 분석 필터를 Service에 올바른 순서로 전달하면 200을 반환한다`() {
            given(
                jobSearchService.search(
                    query = any(),
                    postingType = any(),
                    applicationMethod = eq(ApplicationMethod.EXTERNAL),
                    status = any(),
                    companyType = any(),
                    sourceName = any(),
                    targetGrade = any(),
                    highSchoolGraduateFit = eq(AiFitLevel.SUITABLE),
                    entryLevelFit = eq(AiFitLevel.CONDITIONAL),
                    difficulty = eq(AiDifficulty.EASY),
                    openOnly = anyBoolean(),
                    sort = anySort(),
                    direction = any(),
                    pageable = anyPageable(),
                    requesterId = anyLong(),
                ),
            ).willReturn(searchResponse())

            mockMvc
                .perform(
                    get("/api/v1/jobs")
                        .param("highSchoolGraduateFit", "SUITABLE")
                        .param("entryLevelFit", "CONDITIONAL")
                        .param("difficulty", "EASY")
                        .param("applicationMethod", "EXTERNAL")
                        .with(authOf(1L, "STUDENT")),
                ).andExpect(status().isOk)
        }

        @Test
        fun `잘못된 지원 방식 필터 값은 400과 TYPE_MISMATCH를 반환한다`() {
            mockMvc
                .perform(get("/api/v1/jobs").param("applicationMethod", "BOGUS").with(authOf(1L, "STUDENT")))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("TYPE_MISMATCH"))
        }

        // --- Fixture ---

        private fun anySort(): JobSort = any(JobSort::class.java) ?: JobSort.LATEST

        private fun anyPageable(): Pageable = any(Pageable::class.java) ?: PageRequest.of(0, 20)

        private fun searchResponse() =
            JobSearchResponse(
                content = listOf(summaryResponse()),
                page = 0,
                size = 20,
                totalElements = 1,
                totalPages = 1,
                first = true,
                last = true,
            )

        private fun summaryResponse() =
            JobSummaryResponse(
                jobId = 1L,
                title = "2026 상반기 백엔드 채용",
                postingType = PostingType.MOU,
                applicationMethod = ApplicationMethod.EXTERNAL,
                status = JobStatus.PUBLISHED,
                company = CompanySummary(1L, "인력개발원", logoUrl = "https://storage.example/company-logo?signature=test"),
                startDate = null,
                endDate = null,
                targetGrade = 3,
                capacity = 2,
                location = "서울특별시 중구",
                employmentType = "인턴",
                firstComeServed = false,
                viewCount = 10,
                publishedAt = LocalDateTime.of(2026, 7, 25, 9, 0),
                application =
                    JobApplicationEligibilityAccessSnapshot(
                        canApply = true,
                        eligibilityReason = "AVAILABLE",
                        eligibilityMessage = "지원 가능한 공고입니다.",
                        applicationId = null,
                        applicationStatus = null,
                        availableActions = listOf("CREATE_DRAFT"),
                    ),
                bookmarked = true,
            )
    }
