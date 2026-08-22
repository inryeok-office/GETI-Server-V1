package team.inreok.getiserver.domain.company.controller

import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.company.dto.CompanyOpenJobResponse
import team.inreok.getiserver.domain.company.dto.CompanyResponse
import team.inreok.getiserver.domain.company.dto.CompanySearchResponse
import team.inreok.getiserver.domain.company.dto.CompanySummaryResponse
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.company.entity.type.MouStatus
import team.inreok.getiserver.domain.company.exception.CompanyNotFoundException
import team.inreok.getiserver.domain.company.service.CompanyService
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig
import java.time.LocalDate
import java.time.LocalDateTime

// SecurityConfig를 명시적으로 Import해 /api/v1/companies가 실제로 인증을 요구하는지(401)까지 검증한다.
@WebMvcTest(controllers = [CompanyController::class])
@Import(team.inreok.getiserver.global.security.NormalSecurityTestConfig::class)
@EnableWebSecurity
class CompanyControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var companyService: CompanyService

        @MockitoBean
        private lateinit var jwtTokenProvider: JwtTokenProvider

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

        private fun companyResponse() =
            CompanyResponse(
                companyId = 1L,
                name = "인력개발원",
                companyType = CompanyType.PUBLIC_INSTITUTION,
                mouStatus = MouStatus.ACTIVE,
                sourceName = "manual",
                homepageUrl = "https://example.com",
                logoUrl = null,
                description = "교육 기관",
                industry = "소프트웨어 개발",
                address = "대구광역시 남구 대명동",
                mouStartDate = LocalDate.of(2026, 3, 1),
                mouEndDate = LocalDate.of(2027, 2, 28),
                createdAt = LocalDateTime.of(2026, 3, 1, 10, 15, 30),
                updatedAt = LocalDateTime.of(2026, 3, 2, 9, 0, 0),
                openJobs =
                    listOf(
                        CompanyOpenJobResponse(
                            jobId = 10L,
                            title = "Backend 개발자 채용",
                            postingType = "MOU",
                            applicationMethod = "INTERNAL",
                            status = "PUBLISHED",
                            startDate = LocalDateTime.of(2026, 3, 1, 9, 0),
                            endDate = LocalDateTime.of(2026, 3, 31, 18, 0),
                            location = "서울",
                            employmentType = "인턴",
                            sourceName = null,
                        ),
                    ),
            )

        @Test
        fun `기업 상세를 조회하면 200과 함께 기업 정보를 반환한다`() {
            given(companyService.get(1L, 1L)).willReturn(companyResponse())

            mockMvc
                .perform(get("/api/v1/companies/1").with(authOf(1L, "STUDENT")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.companyId").value(1))
                .andExpect(jsonPath("$.data.name").value("인력개발원"))
                .andExpect(jsonPath("$.data.companyType").value("PUBLIC_INSTITUTION"))
                .andExpect(jsonPath("$.data.mouStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.sourceName").value("manual"))
                .andExpect(jsonPath("$.data.homepageUrl").value("https://example.com"))
                .andExpect(jsonPath("$.data.industry").value("소프트웨어 개발"))
                .andExpect(jsonPath("$.data.address").value("대구광역시 남구 대명동"))
                .andExpect(jsonPath("$.data.mouStartDate").value("2026-03-01"))
                .andExpect(jsonPath("$.data.representativeEmail").doesNotExist())
                .andExpect(jsonPath("$.data.representativePhone").doesNotExist())
                .andExpect(jsonPath("$.data.memo").doesNotExist())
                .andExpect(jsonPath("$.data.openJobs[0].jobId").value(10))
                .andExpect(jsonPath("$.data.openJobs[0].applicationMethod").value("INTERNAL"))
                // File 도메인 연동 전이라 logoUrl은 항상 null로 응답한다.
                .andExpect(jsonPath("$.data.logoUrl").isEmpty)
        }

        @Test
        fun `교사도 기업 상세를 조회할 수 있다`() {
            // requesterId는 인증 주체(여기서는 교사 2L)가 그대로 전달되어야 한다.
            given(companyService.get(1L, 2L)).willReturn(companyResponse())

            mockMvc
                .perform(get("/api/v1/companies/1").with(authOf(2L, "TEACHER")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.companyId").value(1))
        }

        @Test
        fun `존재하지 않는 기업을 조회하면 404 COMPANY_NOT_FOUND를 반환한다`() {
            given(companyService.get(999L, 1L)).willThrow(CompanyNotFoundException(999L))

            mockMvc
                .perform(get("/api/v1/companies/999").with(authOf(1L, "STUDENT")))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMPANY_NOT_FOUND"))
        }

        @Test
        fun `인증되지 않은 상세 조회는 401 UNAUTHORIZED를 반환한다`() {
            mockMvc
                .perform(get("/api/v1/companies/1"))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        }

        @Test
        fun `기업 목록을 조회하면 200과 함께 Page 정보를 반환한다`() {
            given(companyService.search(1L, null, null, null, null, PageRequest.of(0, 20)))
                .willReturn(
                    CompanySearchResponse(
                        content =
                            listOf(
                                CompanySummaryResponse(
                                    companyId = 1L,
                                    name = "인력개발원",
                                    companyType = CompanyType.GENERAL,
                                    mouStatus = MouStatus.NONE,
                                    logoUrl = null,
                                    openJobCount = 2L,
                                    activeMouJobCount = 1L,
                                    applicationCount = 24L,
                                ),
                            ),
                        page = 0,
                        size = 20,
                        totalElements = 1,
                        totalPages = 1,
                        first = true,
                        last = true,
                    ),
                )

            mockMvc
                .perform(get("/api/v1/companies").with(authOf(1L, "STUDENT")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].companyId").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("인력개발원"))
                .andExpect(jsonPath("$.data.content[0].openJobCount").value(2))
                .andExpect(jsonPath("$.data.content[0].activeMouJobCount").value(1))
                .andExpect(jsonPath("$.data.content[0].applicationCount").value(24))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.first").value(true))
                .andExpect(jsonPath("$.data.last").value(true))
        }

        @Test
        fun `검색 조건을 Query Parameter로 전달하면 Service에 그대로 전달된다`() {
            given(
                companyService.search(
                    1L,
                    "인력",
                    CompanyType.PUBLIC_ENTERPRISE,
                    MouStatus.ACTIVE,
                    "collector",
                    PageRequest.of(0, 20),
                ),
            ).willReturn(
                CompanySearchResponse(
                    content = emptyList(),
                    page = 0,
                    size = 20,
                    totalElements = 0,
                    totalPages = 0,
                    first = true,
                    last = true,
                ),
            )

            mockMvc
                .perform(
                    get("/api/v1/companies")
                        .with(authOf(1L, "STUDENT"))
                        .param("query", "인력")
                        .param("companyType", "PUBLIC_ENTERPRISE")
                        .param("mouStatus", "ACTIVE")
                        .param("sourceName", "collector"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.content.length()").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(0))
        }

        @Test
        fun `잘못된 기업 유형 값을 보내면 400을 반환한다`() {
            mockMvc
                .perform(
                    get("/api/v1/companies")
                        .with(authOf(1L, "STUDENT"))
                        .param("companyType", "NOT_A_TYPE"),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `인증되지 않은 목록 조회는 401 UNAUTHORIZED를 반환한다`() {
            mockMvc
                .perform(get("/api/v1/companies"))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        }
    }
