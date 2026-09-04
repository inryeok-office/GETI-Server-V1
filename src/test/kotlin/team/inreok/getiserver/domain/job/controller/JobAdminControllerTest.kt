package team.inreok.getiserver.domain.job.controller

import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.company.query.CompanySummary
import team.inreok.getiserver.domain.job.access.JobApplicationEligibilityAccessSnapshot
import team.inreok.getiserver.domain.job.dto.JobAdminDetailResponse
import team.inreok.getiserver.domain.job.dto.JobAdminListItemResponse
import team.inreok.getiserver.domain.job.dto.JobAdminListResponse
import team.inreok.getiserver.domain.job.dto.JobCreateRequest
import team.inreok.getiserver.domain.job.dto.JobDetailResponse
import team.inreok.getiserver.domain.job.dto.JobManagerResponse
import team.inreok.getiserver.domain.job.dto.JobStatusUpdateRequest
import team.inreok.getiserver.domain.job.dto.JobUpdateRequest
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.entity.type.PostingType
import team.inreok.getiserver.domain.job.exception.JobCompanyNotFoundException
import team.inreok.getiserver.domain.job.exception.JobFormRequiredException
import team.inreok.getiserver.domain.job.exception.JobNotFoundException
import team.inreok.getiserver.domain.job.exception.JobStatusTransitionInvalidException
import team.inreok.getiserver.domain.job.exception.JobValidationFailedException
import team.inreok.getiserver.domain.job.service.JobService
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig
import java.time.LocalDateTime

// SecurityConfig를 명시적으로 Import해 /api/v1/admin/jobs가 실제로 TEACHER 또는 DEVELOPER
// 권한을 요구하는지(401/403)까지 검증한다.
@WebMvcTest(controllers = [JobAdminController::class])
@Import(team.inreok.getiserver.global.security.NormalSecurityTestConfig::class)
@EnableWebSecurity
class JobAdminControllerTest
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

        @Test
        fun `교사는 관리자 공고 목록을 검색과 상태 필터로 조회한다`() {
            given(jobService.listForAdmin("백엔드", JobStatus.DRAFT, PageRequest.of(1, 2), 7L))
                .willReturn(adminListResponse())

            mockMvc
                .perform(
                    get("/api/v1/admin/jobs")
                        .with(authOf(7L, "TEACHER"))
                        .param("query", "백엔드")
                        .param("status", "DRAFT")
                        .param("page", "1")
                        .param("size", "2"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].jobId").value(1))
                .andExpect(jsonPath("$.data.content[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(1))
        }

        @Test
        fun `개발자도 관리자 공고 목록을 조회할 수 있다`() {
            given(jobService.listForAdmin(null, null, PageRequest.of(0, 20), 7L)).willReturn(adminListResponse())

            mockMvc
                .perform(get("/api/v1/admin/jobs").with(authOf(7L, "DEVELOPER")))
                .andExpect(status().isOk)
        }

        @Test
        fun `학생은 관리자 공고 목록을 조회할 수 없다`() {
            mockMvc
                .perform(get("/api/v1/admin/jobs").with(authOf(7L, "STUDENT")))
                .andExpect(status().isForbidden)

            verify(jobService, never()).listForAdmin(any(), any(), anyPageable(), anyLong())
        }

        @Test
        fun `인증 없이 관리자 공고 목록을 조회할 수 없다`() {
            mockMvc
                .perform(get("/api/v1/admin/jobs"))
                .andExpect(status().isUnauthorized)

            verify(jobService, never()).listForAdmin(any(), any(), anyPageable(), anyLong())
        }

        @Test
        fun `잘못된 상태 값은 400을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/admin/jobs").with(authOf(7L, "TEACHER")).param("status", "INVALID"))
                .andExpect(status().isBadRequest)

            verify(jobService, never()).listForAdmin(any(), any(), anyPageable(), anyLong())
        }

        // --- 등록 ---

        @Test
        fun `교사가 공고를 등록하면 201을 반환한다`() {
            given(jobService.create(anyCreateRequest(), anyLong())).willReturn(detailResponse())

            mockMvc
                .perform(
                    post("/api/v1/admin/jobs")
                        .with(authOf(7L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobId").value(1))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
        }

        @Test
        fun `개발자도 공고를 등록할 수 있다`() {
            given(jobService.create(anyCreateRequest(), anyLong())).willReturn(detailResponse())

            mockMvc
                .perform(
                    post("/api/v1/admin/jobs")
                        .with(authOf(7L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()),
                ).andExpect(status().isCreated)
        }

        @Test
        fun `공고 규칙을 위반하면 400을 반환한다`() {
            willThrow(JobValidationFailedException("공고 제목은 비어 있을 수 없습니다."))
                .given(jobService)
                .create(anyCreateRequest(), anyLong())

            mockMvc
                .perform(
                    post("/api/v1/admin/jobs")
                        .with(authOf(7L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(title = "   ")),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("JOB_VALIDATION_FAILED"))
        }

        @Test
        fun `내부 지원 공고를 게시하려 하면 400과 JOB_FORM_REQUIRED를 반환한다`() {
            willThrow(JobFormRequiredException()).given(jobService).create(anyCreateRequest(), anyLong())

            mockMvc
                .perform(
                    post("/api/v1/admin/jobs")
                        .with(authOf(7L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(applicationMethod = "INTERNAL", status = "PUBLISHED")),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("JOB_FORM_REQUIRED"))
        }

        @Test
        fun `없는 기업으로 등록하면 404와 COMPANY_NOT_FOUND를 반환한다`() {
            willThrow(JobCompanyNotFoundException(999L)).given(jobService).create(anyCreateRequest(), anyLong())

            mockMvc
                .perform(
                    post("/api/v1/admin/jobs")
                        .with(authOf(7L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(companyId = 999)),
                ).andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("COMPANY_NOT_FOUND"))
        }

        @Test
        fun `제목이 500자를 넘으면 400과 VALIDATION_FAILED를 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/admin/jobs")
                        .with(authOf(7L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(title = "가".repeat(501))),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))

            verify(jobService, never()).create(anyCreateRequest(), anyLong())
        }

        @Test
        fun `등록 응답에 근무지역과 고용형태가 포함된다`() {
            given(jobService.create(anyCreateRequest(), anyLong())).willReturn(detailResponse())

            mockMvc
                .perform(
                    post("/api/v1/admin/jobs")
                        .with(authOf(7L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.data.location").value("서울특별시 중구"))
                .andExpect(jsonPath("$.data.employmentType").value("인턴"))
        }

        // 사용자가 직접 입력하는 경로는 잘라 저장하지 않고 거절한다. 외부 수집 경로만
        // 잘라 저장하며(CollectedJobUpsertUseCaseImpl.normalizeShortText), 그쪽은 값을 고칠 수 있는
        // 주체가 없어 공고 전체를 잃지 않는 쪽을 택한 것이다(Issue #169).
        @Test
        fun `근무지역이 255자를 넘으면 400과 VALIDATION_FAILED를 반환한다`() {
            val body =
                """
                {
                  "companyId": 1,
                  "postingType": "MOU",
                  "applicationMethod": "EXTERNAL",
                  "title": "2026 상반기 백엔드 채용",
                  "status": "DRAFT",
                  "location": "${"가".repeat(256)}"
                }
                """.trimIndent()

            mockMvc
                .perform(
                    post("/api/v1/admin/jobs")
                        .with(authOf(7L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))

            verify(jobService, never()).create(anyCreateRequest(), anyLong())
        }

        // --- 권한 ---

        @Test
        fun `인증 없이 등록하면 401을 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/admin/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))

            verify(jobService, never()).create(anyCreateRequest(), anyLong())
        }

        @Test
        fun `학생이 등록하면 403을 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/admin/jobs")
                        .with(authOf(7L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))

            verify(jobService, never()).create(anyCreateRequest(), anyLong())
        }

        @Test
        fun `학생이 관리자 상세를 조회하면 403을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/admin/jobs/1").with(authOf(7L, "STUDENT")))
                .andExpect(status().isForbidden)
        }

        // --- 수정 ---

        @Test
        fun `공고를 부분 수정하면 200을 반환한다`() {
            given(
                jobService.update(anyLong(), anyUpdateRequest(), anyLong()),
            ).willReturn(detailResponse(title = "새 제목"))

            mockMvc
                .perform(
                    patch("/api/v1/admin/jobs/1")
                        .with(authOf(7L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"title":"새 제목"}"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.title").value("새 제목"))
                .andExpect(
                    jsonPath("$.data.company.logoUrl").value("https://storage.example/company-logo?signature=test"),
                )
        }

        @Test
        fun `수정 요청의 고용형태가 255자를 넘으면 400과 VALIDATION_FAILED를 반환한다`() {
            mockMvc
                .perform(
                    patch("/api/v1/admin/jobs/1")
                        .with(authOf(7L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"employmentType":"${"가".repeat(256)}"}"""),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))

            verify(jobService, never()).update(anyLong(), anyUpdateRequest(), anyLong())
        }

        @Test
        fun `없는 공고를 수정하면 404를 반환한다`() {
            willThrow(JobNotFoundException(1L)).given(jobService).update(anyLong(), anyUpdateRequest(), anyLong())

            mockMvc
                .perform(
                    patch("/api/v1/admin/jobs/1")
                        .with(authOf(7L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"title":"새 제목"}"""),
                ).andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("JOB_NOT_FOUND"))
        }

        // --- 상태 변경 ---

        @Test
        fun `상태를 변경하면 200을 반환한다`() {
            given(jobService.changeStatus(anyLong(), anyStatusRequest(), anyLong()))
                .willReturn(detailResponse(status = JobStatus.PUBLISHED))

            mockMvc
                .perform(
                    patch("/api/v1/admin/jobs/1/status")
                        .with(authOf(7L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"status":"PUBLISHED"}"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
        }

        @Test
        fun `허용되지 않은 상태 전이는 409를 반환한다`() {
            willThrow(JobStatusTransitionInvalidException(JobStatus.CLOSED, JobStatus.PUBLISHED))
                .given(jobService)
                .changeStatus(anyLong(), anyStatusRequest(), anyLong())

            mockMvc
                .perform(
                    patch("/api/v1/admin/jobs/1/status")
                        .with(authOf(7L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"status":"PUBLISHED"}"""),
                ).andExpect(status().isConflict)
                .andExpect(jsonPath("$.error.code").value("JOB_STATUS_TRANSITION_INVALID"))
        }

        // --- 관리자 상세 ---

        @Test
        fun `관리자는 임시저장 공고의 상세를 조회할 수 있다`() {
            given(jobService.getForAdmin(1L, 7L)).willReturn(
                adminDetailResponse(status = JobStatus.DRAFT, manager = JobManagerResponse(2L, "담당 교사")),
            )

            mockMvc
                .perform(get("/api/v1/admin/jobs/1").with(authOf(7L, "TEACHER")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.manager.memberId").value(2))
                .andExpect(jsonPath("$.data.manager.name").value("담당 교사"))
        }

        @Test
        fun `관리자 상세에서 없는 공고는 404를 반환한다`() {
            willThrow(JobNotFoundException(1L)).given(jobService).getForAdmin(anyLong(), anyLong())

            mockMvc
                .perform(get("/api/v1/admin/jobs/1").with(authOf(7L, "DEVELOPER")))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("JOB_NOT_FOUND"))
        }

        // --- Fixture ---
        //
        // Kotlin non-null 파라미터에 bare any()를 쓰면 null이 반환되어 NPE가 나므로 Elvis로
        // 기본값을 준다(CompanyServiceTest.anyCompany와 같은 이유).

        private fun anyCreateRequest(): JobCreateRequest =
            any(JobCreateRequest::class.java) ?: JobCreateRequest(
                companyId = 1L,
                postingType = PostingType.MOU,
                applicationMethod = ApplicationMethod.EXTERNAL,
                title = "제목",
                status = JobStatus.DRAFT,
            )

        private fun anyUpdateRequest(): JobUpdateRequest = any(JobUpdateRequest::class.java) ?: JobUpdateRequest()

        private fun anyStatusRequest(): JobStatusUpdateRequest =
            any(JobStatusUpdateRequest::class.java) ?: JobStatusUpdateRequest(JobStatus.PUBLISHED)

        private fun anyPageable(): org.springframework.data.domain.Pageable =
            any(org.springframework.data.domain.Pageable::class.java) ?: PageRequest.of(0, 20)

        private fun adminListResponse() =
            JobAdminListResponse(
                content =
                    listOf(
                        JobAdminListItemResponse(
                            jobId = 1L,
                            title = "2026 상반기 백엔드 채용",
                            company = CompanySummary(1L, "인력개발원"),
                            postingType = PostingType.MOU,
                            applicationMethod = ApplicationMethod.EXTERNAL,
                            status = JobStatus.DRAFT,
                            startDate = null,
                            endDate = null,
                            createdAt = null,
                            updatedAt = null,
                        ),
                    ),
                page = 1,
                size = 2,
                totalElements = 1,
                totalPages = 1,
                first = false,
                last = true,
            )

        private fun createBody(
            companyId: Long = 1L,
            title: String = "2026 상반기 백엔드 채용",
            applicationMethod: String = "EXTERNAL",
            status: String = "DRAFT",
        ) = """
            {
              "companyId": $companyId,
              "postingType": "MOU",
              "applicationMethod": "$applicationMethod",
              "title": "$title",
              "status": "$status"
            }
            """.trimIndent()

        private fun detailResponse(
            title: String = "2026 상반기 백엔드 채용",
            status: JobStatus = JobStatus.DRAFT,
        ) = JobDetailResponse(
            jobId = 1L,
            title = title,
            postingType = PostingType.MOU,
            applicationMethod = ApplicationMethod.EXTERNAL,
            status = status,
            company = CompanySummary(1L, "인력개발원", logoUrl = "https://storage.example/company-logo?signature=test"),
            content = "## 모집 부문",
            externalUrl = "https://example.com/apply",
            startDate = null,
            endDate = null,
            targetGrade = 3,
            capacity = 2,
            location = "서울특별시 중구",
            employmentType = "인턴",
            firstComeServed = false,
            viewCount = 0,
            publishedAt = null,
            closedAt = null,
            createdAt = LocalDateTime.of(2026, 7, 20, 10, 0),
            updatedAt = LocalDateTime.of(2026, 7, 20, 10, 0),
            aiAnalysis = null,
            application =
                JobApplicationEligibilityAccessSnapshot(
                    canApply = false,
                    eligibilityReason = "NOT_ENROLLED",
                    eligibilityMessage = "재학 중인 학생만 지원할 수 있습니다.",
                    applicationId = null,
                    applicationStatus = null,
                    availableActions = emptyList(),
                ),
            bookmarked = false,
            files = emptyList(),
        )

        private fun adminDetailResponse(
            status: JobStatus = JobStatus.DRAFT,
            manager: JobManagerResponse? = null,
        ) = JobAdminDetailResponse.from(detailResponse(status = status), manager)
    }
