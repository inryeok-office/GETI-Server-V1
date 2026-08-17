package team.inreok.getiserver.domain.application.controller

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willAnswer
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.application.exception.ApplicationReviewForbiddenException
import team.inreok.getiserver.domain.application.exception.JobNotFoundException
import team.inreok.getiserver.domain.application.service.JobApplicationExportService
import team.inreok.getiserver.domain.file.archive.FileArchiveEntry
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig
import java.io.ByteArrayOutputStream
import java.io.OutputStream

@WebMvcTest(controllers = [JobApplicationExportController::class])
@Import(SecurityConfig::class)
@EnableWebSecurity
class JobApplicationExportControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var jobApplicationExportService: JobApplicationExportService

        @MockitoBean
        private lateinit var jwtTokenProvider: JwtTokenProvider

        private fun authOf(
            memberId: Long,
            vararg roles: String,
        ) = authentication(
            UsernamePasswordAuthenticationToken(memberId, null, roles.map { SimpleGrantedAuthority("ROLE_$it") }),
        )

        // Kotlin non-null 파라미터(OutputStream)에 bare any()를 쓰면 null 반환으로 NPE가 나므로
        // Elvis로 Fallback을 둔다(JobApplicationAdminControllerTest.anyPageable과 동일한 관례).
        private fun anyOutputStream(): OutputStream = any(OutputStream::class.java) ?: ByteArrayOutputStream()

        @Test
        fun `담당 교사는 ZIP을 내려받는다`() {
            val entries = listOf(FileArchiveEntry(1L, "홍길동_resume.pdf"))
            given(jobApplicationExportService.buildExportEntries(1L, 100L, false)).willReturn(entries)
            willAnswer { invocation ->
                val output = invocation.getArgument<OutputStream>(1)
                output.write("PK".toByteArray())
                null
            }.given(jobApplicationExportService).writeZip(anyList(), anyOutputStream())

            val result =
                mockMvc
                    .perform(get("/api/v1/admin/jobs/1/applications/export").with(authOf(100L, "TEACHER")))
                    .andExpect(status().isOk)
                    .andExpect(header().string("Content-Type", "application/zip"))
                    .andExpect(
                        header().string("Content-Disposition", "attachment; filename=\"job-1-applications.zip\""),
                    ).andReturn()

            assertThat(result.response.contentAsByteArray).isEqualTo("PK".toByteArray())
        }

        @Test
        fun `담당자가 아닌 교사는 403이다`() {
            given(jobApplicationExportService.buildExportEntries(1L, 999L, false))
                .willThrow(ApplicationReviewForbiddenException())

            mockMvc
                .perform(get("/api/v1/admin/jobs/1/applications/export").with(authOf(999L, "TEACHER")))
                .andExpect(status().isForbidden)
        }

        @Test
        fun `존재하지 않는 공고는 404다`() {
            given(jobApplicationExportService.buildExportEntries(anyLong(), anyLong(), anyBoolean()))
                .willThrow(JobNotFoundException(999L))

            mockMvc
                .perform(get("/api/v1/admin/jobs/999/applications/export").with(authOf(100L, "TEACHER")))
                .andExpect(status().isNotFound)
        }

        @Test
        fun `인증 없이 호출하면 401이다`() {
            mockMvc
                .perform(get("/api/v1/admin/jobs/1/applications/export"))
                .andExpect(status().isUnauthorized)
        }
    }
