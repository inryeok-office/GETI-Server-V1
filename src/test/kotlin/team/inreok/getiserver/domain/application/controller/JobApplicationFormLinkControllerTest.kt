package team.inreok.getiserver.domain.application.controller

import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.application.dto.JobApplicationFormLinkRequest
import team.inreok.getiserver.domain.application.dto.JobApplicationFormLinkResponse
import team.inreok.getiserver.domain.application.exception.FormNotActiveException
import team.inreok.getiserver.domain.application.exception.JobManageForbiddenException
import team.inreok.getiserver.domain.application.exception.JobNotFoundException
import team.inreok.getiserver.domain.application.service.JobApplicationFormLinkService
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig
import java.time.LocalDateTime

@WebMvcTest(controllers = [JobApplicationFormLinkController::class])
@Import(SecurityConfig::class)
@EnableWebSecurity
class JobApplicationFormLinkControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var jobApplicationFormLinkService: JobApplicationFormLinkService

        @MockitoBean
        private lateinit var jwtTokenProvider: JwtTokenProvider

        private fun authOf(
            memberId: Long,
            vararg roles: String,
        ) = authentication(
            UsernamePasswordAuthenticationToken(memberId, null, roles.map { SimpleGrantedAuthority("ROLE_$it") }),
        )

        private fun anyLinkRequest(): JobApplicationFormLinkRequest =
            any(JobApplicationFormLinkRequest::class.java) ?: JobApplicationFormLinkRequest(formId = 0)

        private val fixedTime = LocalDateTime.of(2026, 3, 1, 10, 0, 0)

        @Test
        fun `교사가 공고에 양식을 연결하면 200과 함께 결과를 반환한다`() {
            given(jobApplicationFormLinkService.link(anyLong(), anyLong(), anyBoolean(), anyLinkRequest()))
                .willReturn(
                    JobApplicationFormLinkResponse(jobId = 1L, formId = 10L, formVersion = 1, updatedAt = fixedTime),
                )

            mockMvc
                .perform(
                    post("/api/v1/admin/jobs/1/application-form")
                        .with(authOf(100L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "formId": 10 }"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.jobId").value(1))
                .andExpect(jsonPath("$.data.formId").value(10))
        }

        @Test
        fun `학생은 양식을 연결할 수 없고 403을 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/admin/jobs/1/application-form")
                        .with(authOf(1L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "formId": 10 }"""),
                ).andExpect(status().isForbidden)
        }

        @Test
        fun `인증 없이 연결하면 401 UNAUTHORIZED를 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/admin/jobs/1/application-form")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "formId": 10 }"""),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        }

        @Test
        fun `존재하지 않는 공고면 404 JOB_NOT_FOUND를 반환한다`() {
            given(jobApplicationFormLinkService.link(anyLong(), anyLong(), anyBoolean(), anyLinkRequest()))
                .willThrow(JobNotFoundException(999L))

            mockMvc
                .perform(
                    post("/api/v1/admin/jobs/999/application-form")
                        .with(authOf(100L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "formId": 10 }"""),
                ).andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("JOB_NOT_FOUND"))
        }

        @Test
        fun `담당자가 아니면 403 JOB_MANAGE_FORBIDDEN을 반환한다`() {
            given(jobApplicationFormLinkService.link(anyLong(), anyLong(), anyBoolean(), anyLinkRequest()))
                .willThrow(JobManageForbiddenException())

            mockMvc
                .perform(
                    post("/api/v1/admin/jobs/1/application-form")
                        .with(authOf(100L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "formId": 10 }"""),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("JOB_MANAGE_FORBIDDEN"))
        }

        @Test
        fun `ACTIVE가 아닌 양식이면 400 FORM_NOT_ACTIVE를 반환한다`() {
            given(jobApplicationFormLinkService.link(anyLong(), anyLong(), anyBoolean(), anyLinkRequest()))
                .willThrow(FormNotActiveException())

            mockMvc
                .perform(
                    post("/api/v1/admin/jobs/1/application-form")
                        .with(authOf(100L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "formId": 10 }"""),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("FORM_NOT_ACTIVE"))
        }
    }
