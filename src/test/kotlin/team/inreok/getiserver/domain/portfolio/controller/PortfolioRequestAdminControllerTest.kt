package team.inreok.getiserver.domain.portfolio.controller

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
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.portfolio.dto.PortfolioRequestCreateRequest
import team.inreok.getiserver.domain.portfolio.dto.PortfolioRequestResponse
import team.inreok.getiserver.domain.portfolio.dto.PortfolioRequestStatusUpdateRequest
import team.inreok.getiserver.domain.portfolio.dto.PortfolioRequestUpdateRequest
import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioRequestStatus
import team.inreok.getiserver.domain.portfolio.exception.InvalidTargetStudentException
import team.inreok.getiserver.domain.portfolio.exception.PortfolioStatusTransitionInvalidException
import team.inreok.getiserver.domain.portfolio.exception.TargetStudentRequiredException
import team.inreok.getiserver.domain.portfolio.service.PortfolioRequestService
import team.inreok.getiserver.global.security.JwtTokenProvider
import java.time.LocalDateTime

// SecurityConfig(NormalSecurityTestConfig)를 Import해 /api/v1/admin/portfolio-requests가 실제로
// TEACHER 또는 DEVELOPER 권한을 요구하는지(401/403)까지 검증한다.
@WebMvcTest(controllers = [PortfolioRequestAdminController::class])
@Import(team.inreok.getiserver.global.security.NormalSecurityTestConfig::class)
@EnableWebSecurity
class PortfolioRequestAdminControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var portfolioRequestService: PortfolioRequestService

        @MockitoBean
        private lateinit var jwtTokenProvider: JwtTokenProvider

        // --- 등록 ---

        @Test
        fun `교사가 수합 요청을 등록하면 201을 반환한다`() {
            given(portfolioRequestService.create(anyCreateRequest(), anyLong())).willReturn(response())

            mockMvc
                .perform(
                    post("/api/v1/admin/portfolio-requests")
                        .with(authOf(7L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.requestId").value(1))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.targetCount").value(3))
        }

        @Test
        fun `개발자도 수합 요청을 등록할 수 있다`() {
            given(portfolioRequestService.create(anyCreateRequest(), anyLong())).willReturn(response())

            mockMvc
                .perform(
                    post("/api/v1/admin/portfolio-requests")
                        .with(authOf(7L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()),
                ).andExpect(status().isCreated)
        }

        @Test
        fun `학생은 수합 요청을 등록할 수 없다`() {
            mockMvc
                .perform(
                    post("/api/v1/admin/portfolio-requests")
                        .with(authOf(7L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
            verify(portfolioRequestService, never()).create(anyCreateRequest(), anyLong())
        }

        @Test
        fun `인증이 없으면 401을 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/admin/portfolio-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        }

        @Test
        fun `대상 학생이 없으면 400과 TARGET_STUDENT_REQUIRED를 반환한다`() {
            willThrow(TargetStudentRequiredException())
                .given(portfolioRequestService)
                .create(anyCreateRequest(), anyLong())

            mockMvc
                .perform(
                    post("/api/v1/admin/portfolio-requests")
                        .with(authOf(7L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(targetStudentIds = "[]")),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("TARGET_STUDENT_REQUIRED"))
        }

        @Test
        fun `존재하지 않거나 학생이 아닌 대상이면 400과 INVALID_TARGET_STUDENT를 반환한다`() {
            willThrow(InvalidTargetStudentException())
                .given(portfolioRequestService)
                .create(anyCreateRequest(), anyLong())

            mockMvc
                .perform(
                    post("/api/v1/admin/portfolio-requests")
                        .with(authOf(7L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("INVALID_TARGET_STUDENT"))
        }

        @Test
        fun `제목이 공백이면 400과 VALIDATION_FAILED를 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/admin/portfolio-requests")
                        .with(authOf(7L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(title = "   ")),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
            verify(portfolioRequestService, never()).create(anyCreateRequest(), anyLong())
        }

        // --- 수정 ---

        @Test
        fun `교사가 수합 요청을 수정하면 200을 반환한다`() {
            given(portfolioRequestService.update(anyLong(), anyUpdateRequest())).willReturn(response())

            mockMvc
                .perform(
                    patch("/api/v1/admin/portfolio-requests/1")
                        .with(authOf(7L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"title":"수정한 제목"}"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.requestId").value(1))
        }

        // --- 상태 변경 ---

        @Test
        fun `교사가 상태를 변경하면 200을 반환한다`() {
            given(portfolioRequestService.changeStatus(anyLong(), anyStatusRequest()))
                .willReturn(response(status = PortfolioRequestStatus.PUBLISHED))

            mockMvc
                .perform(
                    patch("/api/v1/admin/portfolio-requests/1/status")
                        .with(authOf(7L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"status":"PUBLISHED"}"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
        }

        @Test
        fun `허용되지 않은 상태 전이는 409를 반환한다`() {
            willThrow(
                PortfolioStatusTransitionInvalidException(PortfolioRequestStatus.DRAFT, PortfolioRequestStatus.CLOSED),
            ).given(portfolioRequestService)
                .changeStatus(anyLong(), anyStatusRequest())

            mockMvc
                .perform(
                    patch("/api/v1/admin/portfolio-requests/1/status")
                        .with(authOf(7L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"status":"CLOSED"}"""),
                ).andExpect(status().isConflict)
                .andExpect(jsonPath("$.error.code").value("PORTFOLIO_STATUS_TRANSITION_INVALID"))
        }

        // --- helpers ---

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

        private fun anyCreateRequest(): PortfolioRequestCreateRequest =
            any(PortfolioRequestCreateRequest::class.java) ?: PortfolioRequestCreateRequest(
                title = "제목",
                dueAt = LocalDateTime.of(2026, 9, 30, 23, 59),
                targetStudentIds = listOf(1L),
            )

        private fun anyUpdateRequest(): PortfolioRequestUpdateRequest =
            any(PortfolioRequestUpdateRequest::class.java) ?: PortfolioRequestUpdateRequest()

        private fun anyStatusRequest(): PortfolioRequestStatusUpdateRequest =
            any(PortfolioRequestStatusUpdateRequest::class.java)
                ?: PortfolioRequestStatusUpdateRequest(PortfolioRequestStatus.PUBLISHED)

        private fun response(status: PortfolioRequestStatus = PortfolioRequestStatus.DRAFT) =
            PortfolioRequestResponse(
                requestId = 1L,
                title = "2026 상반기 포트폴리오 수합",
                description = "설명",
                status = status,
                dueAt = LocalDateTime.of(2026, 9, 30, 23, 59),
                targetCount = 3L,
                submittedCount = 0L,
                createdAt = LocalDateTime.of(2026, 8, 20, 9, 0),
                updatedAt = LocalDateTime.of(2026, 8, 20, 9, 0),
            )

        private fun createBody(
            title: String = "2026 상반기 포트폴리오 수합",
            targetStudentIds: String = "[1, 2, 3]",
        ) = """
            {
              "title": "$title",
              "description": "설명",
              "dueAt": "2026-09-30T23:59:59",
              "targetStudentIds": $targetStudentIds
            }
            """.trimIndent()
    }
