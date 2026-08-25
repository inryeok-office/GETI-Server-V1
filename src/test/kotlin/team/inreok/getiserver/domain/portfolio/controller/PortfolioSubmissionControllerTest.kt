package team.inreok.getiserver.domain.portfolio.controller

import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
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
import team.inreok.getiserver.domain.portfolio.dto.PortfolioSubmissionFileResponse
import team.inreok.getiserver.domain.portfolio.dto.PortfolioSubmissionResponse
import team.inreok.getiserver.domain.portfolio.dto.PortfolioSubmissionUpsertRequest
import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioSubmissionStatus
import team.inreok.getiserver.domain.portfolio.exception.NotRequestTargetException
import team.inreok.getiserver.domain.portfolio.exception.PortfolioRequestNotFoundException
import team.inreok.getiserver.domain.portfolio.exception.SubmissionClosedException
import team.inreok.getiserver.domain.portfolio.service.PortfolioSubmissionService
import team.inreok.getiserver.global.security.JwtTokenProvider
import java.time.LocalDateTime

// SecurityConfig(NormalSecurityTestConfig)를 Import해 /api/v1/portfolio-requests/**가 인증을
// 요구하는지(401)까지 검증한다. 대상 여부·제출 가능 시점 판정은 Service가 하므로 여기서는 계약만 본다.
@WebMvcTest(controllers = [PortfolioSubmissionController::class])
@Import(team.inreok.getiserver.global.security.NormalSecurityTestConfig::class)
@EnableWebSecurity
class PortfolioSubmissionControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var portfolioSubmissionService: PortfolioSubmissionService

        @MockitoBean
        private lateinit var jwtTokenProvider: JwtTokenProvider

        @Test
        fun `대상 학생이 제출하면 200과 결과를 반환한다`() {
            given(portfolioSubmissionService.submit(anyLong(), anyLong(), anyUpsert())).willReturn(submissionResponse())

            mockMvc
                .perform(
                    patch("/api/v1/portfolio-requests/1/submission")
                        .with(authOf(7L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "status": "SUBMITTED", "portfolioUrl": "https://me", "fileIds": [1] }"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.submissionId").value(100))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.files[0].fileId").value(1))
                .andExpect(jsonPath("$.data.files[0].downloadUrl").value("/api/v1/files/1/download"))
        }

        @Test
        fun `대상이 아닌 학생이 제출하면 403과 NOT_REQUEST_TARGET을 반환한다`() {
            willThrow(NotRequestTargetException())
                .given(portfolioSubmissionService)
                .submit(anyLong(), anyLong(), anyUpsert())

            mockMvc
                .perform(
                    patch("/api/v1/portfolio-requests/1/submission")
                        .with(authOf(7L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "status": "SUBMITTED" }"""),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("NOT_REQUEST_TARGET"))
        }

        @Test
        fun `마감된 요청에 제출하면 409와 SUBMISSION_CLOSED를 반환한다`() {
            willThrow(SubmissionClosedException())
                .given(portfolioSubmissionService)
                .submit(anyLong(), anyLong(), anyUpsert())

            mockMvc
                .perform(
                    patch("/api/v1/portfolio-requests/1/submission")
                        .with(authOf(7L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "status": "SUBMITTED" }"""),
                ).andExpect(status().isConflict)
                .andExpect(jsonPath("$.error.code").value("SUBMISSION_CLOSED"))
        }

        @Test
        fun `없는 요청에 제출하면 404와 PORTFOLIO_REQUEST_NOT_FOUND를 반환한다`() {
            willThrow(PortfolioRequestNotFoundException(99L))
                .given(portfolioSubmissionService)
                .submit(anyLong(), anyLong(), anyUpsert())

            mockMvc
                .perform(
                    patch("/api/v1/portfolio-requests/99/submission")
                        .with(authOf(7L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "status": "DRAFT" }"""),
                ).andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("PORTFOLIO_REQUEST_NOT_FOUND"))
        }

        @Test
        fun `인증이 없으면 제출은 401을 반환한다`() {
            mockMvc
                .perform(
                    patch("/api/v1/portfolio-requests/1/submission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "status": "SUBMITTED" }"""),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        }

        // --- helpers ---

        // Kotlin의 non-null 파라미터에 Mockito any()가 null을 돌려주면 매처가 깨지므로, 기존
        // anyPageable() 관례처럼 null일 때 실제 값을 대신 돌려주는 non-null 매처를 만든다.
        private fun anyUpsert(): PortfolioSubmissionUpsertRequest =
            any(PortfolioSubmissionUpsertRequest::class.java)
                ?: PortfolioSubmissionUpsertRequest(status = PortfolioSubmissionStatus.SUBMITTED)

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

        private fun submissionResponse() =
            PortfolioSubmissionResponse(
                submissionId = 100L,
                requestId = 1L,
                status = PortfolioSubmissionStatus.SUBMITTED,
                portfolioUrl = "https://me",
                note = null,
                files =
                    listOf(
                        PortfolioSubmissionFileResponse(
                            fileId = 1L,
                            originalName = "portfolio.pdf",
                            contentType = "application/pdf",
                            size = 1024L,
                            downloadUrl = "/api/v1/files/1/download",
                        ),
                    ),
                submittedAt = LocalDateTime.of(2026, 9, 20, 10, 0),
                updatedAt = LocalDateTime.of(2026, 9, 20, 10, 0),
            )
    }
