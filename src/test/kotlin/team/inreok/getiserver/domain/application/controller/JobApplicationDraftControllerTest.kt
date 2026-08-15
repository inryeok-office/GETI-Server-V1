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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.application.dto.JobApplicationDraftResponse
import team.inreok.getiserver.domain.application.dto.SaveJobApplicationDraftRequest
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus
import team.inreok.getiserver.domain.application.exception.ApplicationAccessForbiddenException
import team.inreok.getiserver.domain.application.exception.ApplicationActionNotAvailableException
import team.inreok.getiserver.domain.application.exception.ApplicationNotFoundException
import team.inreok.getiserver.domain.application.service.JobApplicationService
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig
import java.time.LocalDateTime

@WebMvcTest(controllers = [JobApplicationDraftController::class])
@Import(SecurityConfig::class)
@EnableWebSecurity
class JobApplicationDraftControllerTest
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

        private fun anySaveRequest(): SaveJobApplicationDraftRequest =
            any(SaveJobApplicationDraftRequest::class.java) ?: SaveJobApplicationDraftRequest()

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
                contactPhone = "010-0000-0000",
                privacyConsent = true,
                applicantName = null,
                applicantCohort = null,
                applicantDepartment = null,
                applicantMajors = emptyList(),
                applicantDesiredJob = null,
                applicantTechStacks = emptyList(),
                answers = emptyList(),
                files = emptyList(),
                submittedAt = null,
                withdrawnAt = null,
                createdAt = fixedTime,
                updatedAt = fixedTime,
            )

        @Test
        fun `본인 지원서를 임시저장하면 200과 함께 결과를 반환한다`() {
            given(jobApplicationService.saveDraft(anyLong(), anyLong(), anySaveRequest())).willReturn(draftResponse())

            mockMvc
                .perform(
                    patch("/api/v1/job-applications/1")
                        .with(authOf(1L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "contactPhone": "010-0000-0000", "privacyConsent": true }"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.contactPhone").value("010-0000-0000"))
                .andExpect(jsonPath("$.data.privacyConsent").value(true))
        }

        @Test
        fun `존재하지 않는 지원서면 404 APPLICATION_NOT_FOUND를 반환한다`() {
            given(jobApplicationService.saveDraft(anyLong(), anyLong(), anySaveRequest()))
                .willThrow(ApplicationNotFoundException(999L))

            mockMvc
                .perform(
                    patch("/api/v1/job-applications/999")
                        .with(authOf(1L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"),
                ).andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("APPLICATION_NOT_FOUND"))
        }

        @Test
        fun `다른 학생의 지원서면 403 APPLICATION_ACCESS_FORBIDDEN을 반환한다`() {
            given(jobApplicationService.saveDraft(anyLong(), anyLong(), anySaveRequest()))
                .willThrow(ApplicationAccessForbiddenException())

            mockMvc
                .perform(
                    patch("/api/v1/job-applications/1")
                        .with(authOf(2L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("APPLICATION_ACCESS_FORBIDDEN"))
        }

        @Test
        fun `DRAFT 상태가 아니면 409 APPLICATION_ACTION_NOT_AVAILABLE을 반환한다`() {
            given(jobApplicationService.saveDraft(anyLong(), anyLong(), anySaveRequest()))
                .willThrow(ApplicationActionNotAvailableException())

            mockMvc
                .perform(
                    patch("/api/v1/job-applications/1")
                        .with(authOf(1L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"),
                ).andExpect(status().isConflict)
                .andExpect(jsonPath("$.error.code").value("APPLICATION_ACTION_NOT_AVAILABLE"))
        }

        @Test
        fun `인증 없이 임시저장하면 401을 반환한다`() {
            mockMvc
                .perform(
                    patch("/api/v1/job-applications/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"),
                ).andExpect(status().isUnauthorized)
        }
    }
