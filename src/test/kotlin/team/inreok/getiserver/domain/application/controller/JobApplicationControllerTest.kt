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
import team.inreok.getiserver.domain.application.dto.CreateJobApplicationRequest
import team.inreok.getiserver.domain.application.dto.JobApplicationDraftResponse
import team.inreok.getiserver.domain.application.dto.JobEligibilityResponse
import team.inreok.getiserver.domain.application.entity.type.JobApplicationEligibilityReason
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus
import team.inreok.getiserver.domain.application.exception.ActiveApplicationExistsException
import team.inreok.getiserver.domain.application.exception.JobNotApplicableException
import team.inreok.getiserver.domain.application.service.JobApplicationService
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig
import java.time.LocalDateTime

@WebMvcTest(controllers = [JobApplicationController::class])
@Import(SecurityConfig::class)
@EnableWebSecurity
class JobApplicationControllerTest
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

        private fun anyCreateRequest(): CreateJobApplicationRequest =
            any(CreateJobApplicationRequest::class.java) ?: CreateJobApplicationRequest()

        private val fixedTime = LocalDateTime.of(2026, 3, 1, 10, 0, 0)

        private fun draftResponse() =
            JobApplicationDraftResponse(
                applicationId = 1L,
                jobId = 1L,
                formId = 10L,
                formVersion = 1,
                status = JobApplicationStatus.DRAFT,
                statusReason = null,
                contactEmail = "student@example.com",
                contactPhone = null,
                privacyConsent = false,
                applicantName = null,
                applicantCohort = null,
                applicantDepartment = null,
                applicantMajors = emptyList(),
                applicantDesiredJob = null,
                applicantTechStacks = emptyList(),
                answers = emptyList(),
                submittedAt = null,
                createdAt = fixedTime,
                updatedAt = fixedTime,
            )

        @Test
        fun `지원 가능 여부를 조회하면 200과 함께 결과를 반환한다`() {
            given(jobApplicationService.checkEligibility(1L, 1L))
                .willReturn(
                    JobEligibilityResponse(
                        canApply = true,
                        eligibilityReason = JobApplicationEligibilityReason.AVAILABLE,
                        eligibilityMessage = "지원 가능한 공고입니다.",
                        availableActions = listOf("CREATE_DRAFT"),
                    ),
                )

            mockMvc
                .perform(get("/api/v1/jobs/1/application-eligibility").with(authOf(1L, "STUDENT")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.canApply").value(true))
                .andExpect(jsonPath("$.data.eligibilityReason").value("AVAILABLE"))
        }

        @Test
        fun `인증 없이 지원가능여부를 조회하면 401을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/jobs/1/application-eligibility"))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `지원서 초안을 생성하면 201과 함께 결과를 반환한다`() {
            given(
                jobApplicationService.createDraft(anyLong(), anyLong(), anyCreateRequest()),
            ).willReturn(draftResponse())

            mockMvc
                .perform(
                    post("/api/v1/jobs/1/applications")
                        .with(authOf(1L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "prefillProfileFields": true }"""),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.data.applicationId").value(1))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
        }

        @Test
        fun `지원할 수 없는 공고면 400 JOB_NOT_APPLICABLE을 반환한다`() {
            given(jobApplicationService.createDraft(anyLong(), anyLong(), anyCreateRequest()))
                .willThrow(JobNotApplicableException())

            mockMvc
                .perform(
                    post("/api/v1/jobs/1/applications")
                        .with(authOf(1L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("JOB_NOT_APPLICABLE"))
        }

        @Test
        fun `이미 활성 지원서가 있으면 409 ACTIVE_APPLICATION_EXISTS를 반환한다`() {
            given(jobApplicationService.createDraft(anyLong(), anyLong(), anyCreateRequest()))
                .willThrow(ActiveApplicationExistsException())

            mockMvc
                .perform(
                    post("/api/v1/jobs/1/applications")
                        .with(authOf(1L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"),
                ).andExpect(status().isConflict)
                .andExpect(jsonPath("$.error.code").value("ACTIVE_APPLICATION_EXISTS"))
        }
    }
