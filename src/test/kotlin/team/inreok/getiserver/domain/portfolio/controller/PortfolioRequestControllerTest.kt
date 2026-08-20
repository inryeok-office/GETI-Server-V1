package team.inreok.getiserver.domain.portfolio.controller

import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.eq
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.portfolio.dto.PortfolioRequestListResponse
import team.inreok.getiserver.domain.portfolio.dto.PortfolioRequestResponse
import team.inreok.getiserver.domain.portfolio.dto.PortfolioRequestSummaryResponse
import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioRequestStatus
import team.inreok.getiserver.domain.portfolio.exception.NotRequestTargetException
import team.inreok.getiserver.domain.portfolio.exception.PortfolioRequestNotFoundException
import team.inreok.getiserver.domain.portfolio.service.PortfolioRequestService
import team.inreok.getiserver.global.security.JwtTokenProvider
import java.time.LocalDateTime

// SecurityConfig(NormalSecurityTestConfig)를 Import해 /api/v1/portfolio-requests가 인증을
// 요구하는지(401)까지 검증한다. 학생의 대상 여부 판정은 Service가 하므로 여기서는 계약만 본다.
@WebMvcTest(controllers = [PortfolioRequestController::class])
@Import(team.inreok.getiserver.global.security.NormalSecurityTestConfig::class)
@EnableWebSecurity
class PortfolioRequestControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var portfolioRequestService: PortfolioRequestService

        @MockitoBean
        private lateinit var jwtTokenProvider: JwtTokenProvider

        // --- 목록 ---

        @Test
        fun `학생이 목록을 조회하면 200과 항목을 반환한다`() {
            given(portfolioRequestService.getList(any(), anyLong(), anyBoolean(), anyPageable()))
                .willReturn(listResponse())

            mockMvc
                .perform(get("/api/v1/portfolio-requests").with(authOf(7L, "STUDENT")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].requestId").value(1))
                .andExpect(jsonPath("$.data.content[0].targetCount").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(1))
            // 학생 요청은 isAdmin=false로 위임되어야 한다(Service가 대상·가시성 분기에 사용).
            verify(portfolioRequestService).getList(any(), anyLong(), eq(false), anyPageable())
        }

        @Test
        fun `교사가 목록을 조회하면 isAdmin=true로 위임한다`() {
            given(portfolioRequestService.getList(any(), anyLong(), anyBoolean(), anyPageable()))
                .willReturn(listResponse())

            mockMvc
                .perform(get("/api/v1/portfolio-requests").with(authOf(9L, "TEACHER")))
                .andExpect(status().isOk)
            // 교사·개발자는 관리 가능한 전체 요청을 봐야 하므로 isAdmin=true로 위임되어야 한다.
            verify(portfolioRequestService).getList(any(), anyLong(), eq(true), anyPageable())
        }

        @Test
        fun `인증이 없으면 목록 조회는 401을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/portfolio-requests"))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        }

        // --- 상세 ---

        @Test
        fun `대상 학생은 상세를 조회할 수 있다`() {
            given(portfolioRequestService.getDetail(anyLong(), anyLong(), anyBoolean())).willReturn(detailResponse())

            mockMvc
                .perform(get("/api/v1/portfolio-requests/1").with(authOf(7L, "STUDENT")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.requestId").value(1))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
        }

        @Test
        fun `대상이 아닌 학생이 상세를 조회하면 403과 NOT_REQUEST_TARGET을 반환한다`() {
            willThrow(NotRequestTargetException())
                .given(portfolioRequestService)
                .getDetail(anyLong(), anyLong(), anyBoolean())

            mockMvc
                .perform(get("/api/v1/portfolio-requests/1").with(authOf(7L, "STUDENT")))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("NOT_REQUEST_TARGET"))
        }

        @Test
        fun `없는 요청을 조회하면 404와 PORTFOLIO_REQUEST_NOT_FOUND를 반환한다`() {
            willThrow(PortfolioRequestNotFoundException(99L))
                .given(portfolioRequestService)
                .getDetail(anyLong(), anyLong(), anyBoolean())

            mockMvc
                .perform(get("/api/v1/portfolio-requests/99").with(authOf(7L, "STUDENT")))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("PORTFOLIO_REQUEST_NOT_FOUND"))
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

        private fun anyPageable(): Pageable = any(Pageable::class.java) ?: Pageable.unpaged()

        private fun listResponse() =
            PortfolioRequestListResponse(
                content =
                    listOf(
                        PortfolioRequestSummaryResponse(
                            requestId = 1L,
                            title = "2026 상반기 포트폴리오 수합",
                            status = PortfolioRequestStatus.PUBLISHED,
                            dueAt = LocalDateTime.of(2026, 9, 30, 23, 59),
                            targetCount = 20L,
                            submittedCount = 5L,
                        ),
                    ),
                page = 0,
                size = 20,
                totalElements = 1L,
                totalPages = 1,
                first = true,
                last = true,
            )

        private fun detailResponse() =
            PortfolioRequestResponse(
                requestId = 1L,
                title = "2026 상반기 포트폴리오 수합",
                description = "설명",
                status = PortfolioRequestStatus.PUBLISHED,
                dueAt = LocalDateTime.of(2026, 9, 30, 23, 59),
                targetCount = 20L,
                submittedCount = 5L,
                createdAt = LocalDateTime.of(2026, 8, 20, 9, 0),
                updatedAt = LocalDateTime.of(2026, 8, 20, 9, 0),
            )
    }
