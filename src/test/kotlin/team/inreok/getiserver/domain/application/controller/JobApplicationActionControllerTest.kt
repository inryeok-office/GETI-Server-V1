package team.inreok.getiserver.domain.application.controller

import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.application.dto.JobApplicationAction
import team.inreok.getiserver.domain.application.dto.JobApplicationActionRequest
import team.inreok.getiserver.domain.application.dto.JobApplicationDraftResponse
import team.inreok.getiserver.domain.application.dto.JobApplicationStatusHistoryResponse
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus
import team.inreok.getiserver.domain.application.exception.ApplicationAccessForbiddenException
import team.inreok.getiserver.domain.application.exception.ApplicationActionNotAvailableException
import team.inreok.getiserver.domain.application.exception.ApplicationNotFoundException
import team.inreok.getiserver.domain.application.exception.ApplicationRequiredAnswerMissingException
import team.inreok.getiserver.domain.application.service.JobApplicationService
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig
import java.time.LocalDateTime

@WebMvcTest(controllers = [JobApplicationActionController::class])
@Import(team.inreok.getiserver.global.security.NormalSecurityTestConfig::class)
@EnableWebSecurity
class JobApplicationActionControllerTest
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

        private fun anyActionRequest(): JobApplicationActionRequest =
            any(JobApplicationActionRequest::class.java) ?: JobApplicationActionRequest(JobApplicationAction.SUBMIT)

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
                applicantName = null,
                applicantCohort = null,
                applicantDepartment = null,
                applicantMajors = emptyList(),
                applicantDesiredJob = null,
                applicantTechStacks = emptyList(),
                answers = emptyList(),
                files = emptyList(),
                submittedAt = if (status == JobApplicationStatus.SUBMITTED) fixedTime else null,
                withdrawnAt = if (status == JobApplicationStatus.WITHDRAWN) fixedTime else null,
                createdAt = fixedTime,
                updatedAt = fixedTime,
            )

        @Test
        fun `본인 지원서를 SUBMIT하면 200과 함께 SUBMITTED 결과를 반환한다`() {
            given(jobApplicationService.executeAction(anyLong(), anyLong(), anyActionRequest()))
                .willReturn(draftResponse())

            mockMvc
                .perform(
                    post("/api/v1/job-applications/1/actions")
                        .with(authOf(1L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "action": "SUBMIT" }"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.submittedAt").exists())
        }

        @Test
        fun `존재하지 않는 지원서면 404 APPLICATION_NOT_FOUND를 반환한다`() {
            given(jobApplicationService.executeAction(anyLong(), anyLong(), anyActionRequest()))
                .willThrow(ApplicationNotFoundException(999L))

            mockMvc
                .perform(
                    post("/api/v1/job-applications/999/actions")
                        .with(authOf(1L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "action": "WITHDRAW" }"""),
                ).andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("APPLICATION_NOT_FOUND"))
        }

        @Test
        fun `다른 학생의 지원서면 403 APPLICATION_ACCESS_FORBIDDEN을 반환한다`() {
            given(jobApplicationService.executeAction(anyLong(), anyLong(), anyActionRequest()))
                .willThrow(ApplicationAccessForbiddenException())

            mockMvc
                .perform(
                    post("/api/v1/job-applications/1/actions")
                        .with(authOf(2L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "action": "WITHDRAW" }"""),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("APPLICATION_ACCESS_FORBIDDEN"))
        }

        @Test
        fun `현재 상태에서 허용되지 않는 Action이면 409 APPLICATION_ACTION_NOT_AVAILABLE을 반환한다`() {
            given(jobApplicationService.executeAction(anyLong(), anyLong(), anyActionRequest()))
                .willThrow(ApplicationActionNotAvailableException())

            mockMvc
                .perform(
                    post("/api/v1/job-applications/1/actions")
                        .with(authOf(1L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "action": "SUBMIT" }"""),
                ).andExpect(status().isConflict)
                .andExpect(jsonPath("$.error.code").value("APPLICATION_ACTION_NOT_AVAILABLE"))
        }

        @Test
        fun `SUBMIT 시 필수 답변이 누락되면 400 APPLICATION_REQUIRED_ANSWER_MISSING을 반환한다`() {
            given(jobApplicationService.executeAction(anyLong(), anyLong(), anyActionRequest()))
                .willThrow(ApplicationRequiredAnswerMissingException(listOf("motivation")))

            mockMvc
                .perform(
                    post("/api/v1/job-applications/1/actions")
                        .with(authOf(1L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "action": "SUBMIT" }"""),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("APPLICATION_REQUIRED_ANSWER_MISSING"))
        }

        @Test
        fun `인증 없이 Action을 요청하면 401을 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/job-applications/1/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "action": "SUBMIT" }"""),
                ).andExpect(status().isUnauthorized)
        }

        @Test
        fun `본인 지원서의 상태 이력을 조회하면 200과 함께 이력 목록을 반환한다`() {
            given(jobApplicationService.getHistory(anyLong(), anyLong()))
                .willReturn(
                    listOf(
                        JobApplicationStatusHistoryResponse(
                            historyId = 1L,
                            fromStatus = JobApplicationStatus.DRAFT,
                            toStatus = JobApplicationStatus.SUBMITTED,
                            action = "SUBMIT",
                            actorMemberId = 1L,
                            reason = null,
                            createdAt = fixedTime,
                        ),
                    ),
                )

            mockMvc
                .perform(get("/api/v1/job-applications/1/history").with(authOf(1L, "STUDENT")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data[0].toStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.data[0].action").value("SUBMIT"))
        }

        @Test
        fun `다른 학생의 지원서 이력을 조회하면 403 APPLICATION_ACCESS_FORBIDDEN을 반환한다`() {
            given(jobApplicationService.getHistory(anyLong(), anyLong()))
                .willThrow(ApplicationAccessForbiddenException())

            mockMvc
                .perform(get("/api/v1/job-applications/1/history").with(authOf(2L, "STUDENT")))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("APPLICATION_ACCESS_FORBIDDEN"))
        }

        @Test
        fun `인증 없이 이력을 요청하면 401을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/job-applications/1/history"))
                .andExpect(status().isUnauthorized)
        }
    }
