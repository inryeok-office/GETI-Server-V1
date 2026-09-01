package team.inreok.getiserver.domain.portfolio.controller

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.eq
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.BDDMockito.willAnswer
import org.mockito.BDDMockito.willThrow
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.Pageable
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.file.archive.FileArchiveEntry
import team.inreok.getiserver.domain.portfolio.dto.PortfolioSubmissionStatusListResponse
import team.inreok.getiserver.domain.portfolio.dto.PortfolioSubmissionStatusResponse
import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioMaterialType
import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioSubmissionStatus
import team.inreok.getiserver.domain.portfolio.exception.NoSubmissionsToExportException
import team.inreok.getiserver.domain.portfolio.exception.PortfolioRequestNotFoundException
import team.inreok.getiserver.domain.portfolio.service.PortfolioSubmissionAdminService
import team.inreok.getiserver.global.security.JwtTokenProvider
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.time.LocalDateTime

// SecurityConfig(NormalSecurityTestConfig)를 Import해 /api/v1/admin/portfolio-requests 하위 경로가
// TEACHER 또는 DEVELOPER 권한을 요구하는지(401/403)까지 검증한다.
@WebMvcTest(controllers = [PortfolioSubmissionAdminController::class])
@Import(team.inreok.getiserver.global.security.NormalSecurityTestConfig::class)
@EnableWebSecurity
class PortfolioSubmissionAdminControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var portfolioSubmissionAdminService: PortfolioSubmissionAdminService

        @MockitoBean
        private lateinit var jwtTokenProvider: JwtTokenProvider

        // --- 제출 현황 목록 ---

        @Test
        fun `교사가 제출 현황을 조회하면 200과 목록을 반환한다`() {
            given(portfolioSubmissionAdminService.getSubmissionStatuses(anyLong(), any(), any(), anyPageable()))
                .willReturn(listResponse())

            mockMvc
                .perform(get("/api/v1/admin/portfolio-requests/1/submissions").with(authOf(9L, "TEACHER")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].memberId").value(7))
                .andExpect(jsonPath("$.data.content[0].submitted").value(true))
                .andExpect(jsonPath("$.data.content[0].materialType").value("BOTH"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
        }

        @Test
        fun `submitted와 name Query Parameter가 Service에 전달된다`() {
            given(portfolioSubmissionAdminService.getSubmissionStatuses(anyLong(), any(), any(), anyPageable()))
                .willReturn(listResponse())

            mockMvc
                .perform(
                    get("/api/v1/admin/portfolio-requests/1/submissions")
                        .queryParam("submitted", "true")
                        .queryParam("name", "홍길동")
                        .with(authOf(9L, "TEACHER")),
                ).andExpect(status().isOk)

            then(portfolioSubmissionAdminService)
                .should()
                .getSubmissionStatuses(eq(1L), eq(true), eq("홍길동"), anyPageable())
        }

        @Test
        fun `없는 요청의 현황 조회는 404를 반환한다`() {
            given(portfolioSubmissionAdminService.getSubmissionStatuses(anyLong(), any(), any(), anyPageable()))
                .willThrow(PortfolioRequestNotFoundException(99L))

            mockMvc
                .perform(get("/api/v1/admin/portfolio-requests/99/submissions").with(authOf(9L, "TEACHER")))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("PORTFOLIO_REQUEST_NOT_FOUND"))
        }

        @Test
        fun `학생은 제출 현황을 조회할 수 없다`() {
            mockMvc
                .perform(get("/api/v1/admin/portfolio-requests/1/submissions").with(authOf(9L, "STUDENT")))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
            verify(portfolioSubmissionAdminService, never())
                .getSubmissionStatuses(anyLong(), any(), any(), anyPageable())
        }

        @Test
        fun `인증이 없으면 현황 조회는 401을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/admin/portfolio-requests/1/submissions"))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        }

        // --- 일괄 다운로드 ---

        @Test
        fun `교사는 제출 자료 ZIP을 내려받는다`() {
            given(portfolioSubmissionAdminService.buildExportEntries(1L, false))
                .willReturn(listOf(FileArchiveEntry(11L, "student-7_홍길동_a.pdf")))
            willAnswer { invocation ->
                invocation.getArgument<OutputStream>(1).write("PK".toByteArray())
                null
            }.given(portfolioSubmissionAdminService).writeZip(anyEntries(), anyOutputStream())

            val result =
                mockMvc
                    .perform(get("/api/v1/admin/portfolio-requests/1/submissions/export").with(authOf(9L, "TEACHER")))
                    .andExpect(status().isOk)
                    .andExpect(header().string("Content-Type", "application/zip"))
                    .andExpect(
                        header().string(
                            "Content-Disposition",
                            "attachment; filename=\"portfolio-request-1-submissions.zip\"",
                        ),
                    ).andReturn()

            assertThat(result.response.contentAsByteArray).isEqualTo("PK".toByteArray())
        }

        @Test
        fun `submittedOnly Query Parameter가 Service에 전달된다`() {
            given(portfolioSubmissionAdminService.buildExportEntries(1L, true))
                .willReturn(listOf(FileArchiveEntry(11L, "student-7_홍길동_a.pdf")))
            willAnswer { invocation ->
                invocation.getArgument<OutputStream>(1).write("PK".toByteArray())
                null
            }.given(portfolioSubmissionAdminService).writeZip(anyEntries(), anyOutputStream())

            mockMvc
                .perform(
                    get("/api/v1/admin/portfolio-requests/1/submissions/export")
                        .queryParam("submittedOnly", "true")
                        .with(authOf(9L, "TEACHER")),
                ).andExpect(status().isOk)

            then(portfolioSubmissionAdminService).should().buildExportEntries(1L, true)
        }

        @Test
        fun `내려받을 자료가 없으면 404이고 Content-Disposition을 남기지 않는다`() {
            given(portfolioSubmissionAdminService.buildExportEntries(1L, false))
                .willThrow(NoSubmissionsToExportException())

            val result =
                mockMvc
                    .perform(get("/api/v1/admin/portfolio-requests/1/submissions/export").with(authOf(9L, "TEACHER")))
                    .andExpect(status().isNotFound)
                    .andExpect(jsonPath("$.error.code").value("NO_SUBMISSIONS_TO_EXPORT"))
                    .andReturn()

            assertThat(result.response.getHeader("Content-Disposition")).isNull()
        }

        @Test
        fun `인증이 없으면 다운로드는 401을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/admin/portfolio-requests/1/submissions/export"))
                .andExpect(status().isUnauthorized)
        }

        // --- helpers ---

        private fun authOf(
            memberId: Long,
            vararg roles: String,
        ) = authentication(
            UsernamePasswordAuthenticationToken(memberId, null, roles.map { SimpleGrantedAuthority("ROLE_$it") }),
        )

        private fun anyPageable(): Pageable = any(Pageable::class.java) ?: Pageable.unpaged()

        private fun anyOutputStream(): OutputStream = any(OutputStream::class.java) ?: ByteArrayOutputStream()

        @Suppress("UNCHECKED_CAST")
        private fun anyEntries(): List<FileArchiveEntry> =
            any(List::class.java) as? List<FileArchiveEntry> ?: emptyList()

        private fun listResponse() =
            PortfolioSubmissionStatusListResponse(
                content =
                    listOf(
                        PortfolioSubmissionStatusResponse(
                            memberId = 7L,
                            studentName = "홍길동",
                            cohort = 6,
                            department = "SW_DEVELOPMENT",
                            submitted = true,
                            status = PortfolioSubmissionStatus.SUBMITTED,
                            materialType = PortfolioMaterialType.BOTH,
                            submittedAt = LocalDateTime.of(2026, 9, 20, 10, 0),
                        ),
                    ),
                page = 0,
                size = 20,
                totalElements = 1,
                totalPages = 1,
                first = true,
                last = true,
            )
    }
