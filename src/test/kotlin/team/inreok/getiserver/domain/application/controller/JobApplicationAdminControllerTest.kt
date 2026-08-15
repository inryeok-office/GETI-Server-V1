package team.inreok.getiserver.domain.application.controller

import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.Pageable
import org.springframework.http.MediaType
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
import team.inreok.getiserver.domain.application.dto.JobApplicationAdminAction
import team.inreok.getiserver.domain.application.dto.JobApplicationAdminActionRequest
import team.inreok.getiserver.domain.application.dto.JobApplicationAdminListResponse
import team.inreok.getiserver.domain.application.dto.JobApplicationDraftResponse
import team.inreok.getiserver.domain.application.dto.JobApplicationStatusHistoryResponse
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus
import team.inreok.getiserver.domain.application.exception.ApplicationActionNotAvailableException
import team.inreok.getiserver.domain.application.exception.ApplicationNotFoundException
import team.inreok.getiserver.domain.application.exception.ApplicationReviewForbiddenException
import team.inreok.getiserver.domain.application.service.JobApplicationAdminService
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig
import java.time.LocalDateTime

@WebMvcTest(controllers = [JobApplicationAdminController::class])
@Import(SecurityConfig::class)
@EnableWebSecurity
class JobApplicationAdminControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var jobApplicationAdminService: JobApplicationAdminService

        @MockitoBean
        private lateinit var jwtTokenProvider: JwtTokenProvider

        private fun authOf(
            memberId: Long,
            vararg roles: String,
        ) = authentication(
            UsernamePasswordAuthenticationToken(memberId, null, roles.map { SimpleGrantedAuthority("ROLE_$it") }),
        )

        private fun anyActionRequest(): JobApplicationAdminActionRequest =
            any(JobApplicationAdminActionRequest::class.java)
                ?: JobApplicationAdminActionRequest(JobApplicationAdminAction.APPROVE)

        // Kotlin non-null 파라미터(Pageable)에 bare any()를 쓰면 null 반환으로 NPE가 나므로 Elvis로
        // Fallback을 둔다(InquiryAdminControllerTest.anyPageable과 동일한 관례).
        private fun anyPageable(): Pageable = any(Pageable::class.java) ?: Pageable.unpaged()

        private val fixedTime = LocalDateTime.of(2026, 3, 1, 10, 0, 0)

        private fun draftResponse(status: JobApplicationStatus = JobApplicationStatus.SUBMITTED) =
            JobApplicationDraftResponse(
                applicationId = 1L,
                jobId = 1L,
                formId = 10L,
                formVersion = 1,
                status = status,
                statusReason = null,
                contactEmail = "student@example.com",
                contactPhone = null,
                privacyConsent = true,
                applicantName = "홍길동",
                applicantCohort = null,
                applicantDepartment = null,
                applicantMajors = emptyList(),
                applicantDesiredJob = null,
                applicantTechStacks = emptyList(),
                answers = emptyList(),
                files = emptyList(),
                submittedAt = fixedTime,
                withdrawnAt = null,
                createdAt = fixedTime,
                updatedAt = fixedTime,
            )

        @Test
        fun `교사가 목록을 조회하면 200과 함께 결과를 반환한다`() {
            given(jobApplicationAdminService.list(any(), any(), anyPageable()))
                .willReturn(JobApplicationAdminListResponse(emptyList(), 0, 20, 0, 0, true, true))

            mockMvc
                .perform(get("/api/v1/admin/job-applications").with(authOf(100L, "TEACHER")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.content").isArray)
        }

        @Test
        fun `학생은 목록을 조회할 수 없고 403을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/admin/job-applications").with(authOf(1L, "STUDENT")))
                .andExpect(status().isForbidden)
        }

        @Test
        fun `인증 없이 목록을 조회하면 401을 반환한다`() {
            mockMvc.perform(get("/api/v1/admin/job-applications")).andExpect(status().isUnauthorized)
        }

        @Test
        fun `교사가 상세를 조회하면 200과 함께 결과를 반환한다`() {
            given(jobApplicationAdminService.getDetail(1L)).willReturn(draftResponse())

            mockMvc
                .perform(get("/api/v1/admin/job-applications/1").with(authOf(100L, "TEACHER")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.applicationId").value(1))
        }

        @Test
        fun `존재하지 않는 지원서를 상세 조회하면 404 APPLICATION_NOT_FOUND를 반환한다`() {
            given(jobApplicationAdminService.getDetail(999L)).willThrow(ApplicationNotFoundException(999L))

            mockMvc
                .perform(get("/api/v1/admin/job-applications/999").with(authOf(100L, "TEACHER")))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("APPLICATION_NOT_FOUND"))
        }

        @Test
        fun `담당 교사가 APPROVE Action을 수행하면 200과 함께 결과를 반환한다`() {
            given(jobApplicationAdminService.executeAction(anyLong(), anyLong(), anyBoolean(), anyActionRequest()))
                .willReturn(draftResponse(status = JobApplicationStatus.APPROVED))

            mockMvc
                .perform(
                    post("/api/v1/admin/job-applications/1/actions")
                        .with(authOf(100L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "action": "APPROVE" }"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
        }

        @Test
        fun `학생은 Action을 수행할 수 없고 403을 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/admin/job-applications/1/actions")
                        .with(authOf(1L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "action": "APPROVE" }"""),
                ).andExpect(status().isForbidden)
        }

        @Test
        fun `담당자가 아닌 교사가 Action을 수행하면 403 APPLICATION_REVIEW_FORBIDDEN을 반환한다`() {
            given(jobApplicationAdminService.executeAction(anyLong(), anyLong(), anyBoolean(), anyActionRequest()))
                .willThrow(ApplicationReviewForbiddenException())

            mockMvc
                .perform(
                    post("/api/v1/admin/job-applications/1/actions")
                        .with(authOf(200L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "action": "APPROVE" }"""),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("APPLICATION_REVIEW_FORBIDDEN"))
        }

        @Test
        fun `허용되지 않은 상태에서 Action을 수행하면 409 APPLICATION_ACTION_NOT_AVAILABLE을 반환한다`() {
            given(jobApplicationAdminService.executeAction(anyLong(), anyLong(), anyBoolean(), anyActionRequest()))
                .willThrow(ApplicationActionNotAvailableException())

            mockMvc
                .perform(
                    post("/api/v1/admin/job-applications/1/actions")
                        .with(authOf(100L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "action": "APPROVE" }"""),
                ).andExpect(status().isConflict)
                .andExpect(jsonPath("$.error.code").value("APPLICATION_ACTION_NOT_AVAILABLE"))
        }

        @Test
        fun `인증 없이 Action을 요청하면 401을 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/admin/job-applications/1/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "action": "APPROVE" }"""),
                ).andExpect(status().isUnauthorized)
        }

        @Test
        fun `교사가 이력을 조회하면 200과 함께 이력 목록을 반환한다`() {
            given(jobApplicationAdminService.getHistory(1L))
                .willReturn(
                    listOf(
                        JobApplicationStatusHistoryResponse(
                            historyId = 1L,
                            fromStatus = JobApplicationStatus.SUBMITTED,
                            toStatus = JobApplicationStatus.APPROVED,
                            action = "APPROVE",
                            actorMemberId = 100L,
                            reason = null,
                            createdAt = fixedTime,
                        ),
                    ),
                )

            mockMvc
                .perform(get("/api/v1/admin/job-applications/1/history").with(authOf(100L, "TEACHER")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data[0].action").value("APPROVE"))
        }

        @Test
        fun `DRAFT 지원서의 이력을 조회하면 404 APPLICATION_NOT_FOUND를 반환한다`() {
            given(jobApplicationAdminService.getHistory(1L)).willThrow(ApplicationNotFoundException(1L))

            mockMvc
                .perform(get("/api/v1/admin/job-applications/1/history").with(authOf(100L, "TEACHER")))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("APPLICATION_NOT_FOUND"))
        }

        @Test
        fun `학생은 이력을 조회할 수 없고 403을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/admin/job-applications/1/history").with(authOf(1L, "STUDENT")))
                .andExpect(status().isForbidden)
        }
    }
