package team.inreok.getiserver.domain.company.controller

import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.eq
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.company.dto.CompanyCreateRequest
import team.inreok.getiserver.domain.company.dto.CompanyResponse
import team.inreok.getiserver.domain.company.dto.CompanyUpdateRequest
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.company.entity.type.MouStatus
import team.inreok.getiserver.domain.company.exception.CompanyNameRequiredException
import team.inreok.getiserver.domain.company.exception.CompanyNotFoundException
import team.inreok.getiserver.domain.company.exception.DuplicateCompanyException
import team.inreok.getiserver.domain.company.exception.MouPeriodInvalidException
import team.inreok.getiserver.domain.company.service.CompanyService
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig
import java.time.LocalDate
import java.time.LocalDateTime

// SecurityConfig를 명시적으로 Import해 /api/v1/admin/companies가 실제로 DEVELOPER 권한을
// 요구하는지(401/403)까지 검증한다.
@WebMvcTest(controllers = [CompanyAdminController::class])
@Import(SecurityConfig::class)
@EnableWebSecurity
class CompanyAdminControllerTest
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

        // Kotlin non-null 파라미터에 bare any()를 쓰면 null 반환으로 NPE가 나므로 Elvis로 기본값을
        // 채운다(TokenServiceImplTest의 anyLocalDateTime()과 동일한 방식).
        private fun anyCreateRequest(): CompanyCreateRequest =
            any(CompanyCreateRequest::class.java)
                ?: CompanyCreateRequest(name = "", companyType = CompanyType.GENERAL)

        private fun anyUpdateRequest(): CompanyUpdateRequest =
            any(CompanyUpdateRequest::class.java) ?: CompanyUpdateRequest()

        private fun companyResponse(name: String = "인력개발원") =
            CompanyResponse(
                companyId = 1L,
                name = name,
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
            )

        @Test
        fun `개발자가 기업을 등록하면 201과 함께 등록된 기업을 반환한다`() {
            given(companyService.create(anyCreateRequest(), eq(1L))).willReturn(companyResponse())

            mockMvc
                .perform(
                    post("/api/v1/admin/companies")
                        .with(authOf(1L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            {
                              "name": "인력개발원",
                              "companyType": "PUBLIC_INSTITUTION",
                              "mouStatus": "ACTIVE",
                              "industry": "소프트웨어 개발",
                              "address": "대구광역시 남구 대명동"
                            }
                            """.trimIndent(),
                        ),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.companyId").value(1))
                .andExpect(jsonPath("$.data.name").value("인력개발원"))
                .andExpect(jsonPath("$.data.industry").value("소프트웨어 개발"))
        }

        @Test
        fun `기업명이 공백이면 400 COMPANY_NAME_REQUIRED를 반환한다`() {
            // 공백 판정은 Bean Validation이 아니라 Service가 수행하므로(API 명세서의 Error Code를
            // 맞추기 위함) Controller 계층에서는 예외가 올바른 Error Code로 변환되는지만 검증한다.
            given(companyService.create(anyCreateRequest(), eq(1L)))
                .willThrow(CompanyNameRequiredException())

            mockMvc
                .perform(
                    post("/api/v1/admin/companies")
                        .with(authOf(1L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "name": "   ", "companyType": "GENERAL" }"""),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMPANY_NAME_REQUIRED"))
        }

        @Test
        fun `이미 등록된 기업이면 409 DUPLICATE_COMPANY를 반환한다`() {
            given(companyService.create(anyCreateRequest(), eq(1L)))
                .willThrow(DuplicateCompanyException())

            mockMvc
                .perform(
                    post("/api/v1/admin/companies")
                        .with(authOf(1L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "name": "인력개발원", "companyType": "GENERAL" }"""),
                ).andExpect(status().isConflict)
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_COMPANY"))
                // 요청 값(기업명·유형)을 오류 Message에 되돌려주지 않는다.
                .andExpect(jsonPath("$.error.message").value("이미 등록된 기업입니다."))
        }

        @Test
        fun `MOU 기간이 역전되면 400 MOU_PERIOD_INVALID를 반환한다`() {
            given(companyService.create(anyCreateRequest(), eq(1L))).willThrow(MouPeriodInvalidException())

            mockMvc
                .perform(
                    post("/api/v1/admin/companies")
                        .with(authOf(1L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            {
                              "name": "인력개발원",
                              "companyType": "GENERAL",
                              "mouStartDate": "2027-03-01",
                              "mouEndDate": "2026-02-28"
                            }
                            """.trimIndent(),
                        ),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("MOU_PERIOD_INVALID"))
        }

        @Test
        fun `교사는 기업을 등록할 수 없고 403을 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/admin/companies")
                        .with(authOf(2L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "name": "인력개발원", "companyType": "GENERAL" }"""),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))

            verify(companyService, never()).create(anyCreateRequest(), anyLong())
        }

        @Test
        fun `학생은 기업을 등록할 수 없고 403을 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/admin/companies")
                        .with(authOf(3L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "name": "인력개발원", "companyType": "GENERAL" }"""),
                ).andExpect(status().isForbidden)
        }

        @Test
        fun `인증 없이 기업을 등록하면 401 UNAUTHORIZED를 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/admin/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "name": "인력개발원", "companyType": "GENERAL" }"""),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        }

        @Test
        fun `개발자가 기업을 수정하면 200과 함께 수정된 기업을 반환한다`() {
            given(companyService.update(anyLong(), anyUpdateRequest(), anyLong()))
                .willReturn(companyResponse(name = "인력개발원 본원"))

            mockMvc
                .perform(
                    patch("/api/v1/admin/companies/1")
                        .with(authOf(1L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "name": "인력개발원 본원" }"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.name").value("인력개발원 본원"))
        }

        @Test
        fun `존재하지 않는 기업을 수정하면 404 COMPANY_NOT_FOUND를 반환한다`() {
            given(companyService.update(anyLong(), anyUpdateRequest(), anyLong()))
                .willThrow(CompanyNotFoundException(999L))

            mockMvc
                .perform(
                    patch("/api/v1/admin/companies/999")
                        .with(authOf(1L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "name": "없는기업" }"""),
                ).andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("COMPANY_NOT_FOUND"))
        }

        @Test
        fun `교사는 기업을 수정할 수 없고 403을 반환한다`() {
            mockMvc
                .perform(
                    patch("/api/v1/admin/companies/1")
                        .with(authOf(2L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "name": "인력개발원 본원" }"""),
                ).andExpect(status().isForbidden)

            verify(companyService, never()).update(anyLong(), anyUpdateRequest(), anyLong())
        }

        @Test
        fun `개발자가 기업을 삭제하면 204를 반환한다`() {
            mockMvc
                .perform(delete("/api/v1/admin/companies/1").with(authOf(1L, "DEVELOPER")))
                .andExpect(status().isNoContent)

            verify(companyService).delete(1L)
        }

        @Test
        fun `존재하지 않는 기업을 삭제하면 404 COMPANY_NOT_FOUND를 반환한다`() {
            // delete는 반환값이 없어(Unit → void) given(...)으로 스텁할 수 없으므로 willThrow를 먼저 쓴다.
            willThrow(CompanyNotFoundException(999L)).given(companyService).delete(999L)

            mockMvc
                .perform(delete("/api/v1/admin/companies/999").with(authOf(1L, "DEVELOPER")))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("COMPANY_NOT_FOUND"))
        }

        @Test
        fun `교사는 기업을 삭제할 수 없고 403을 반환한다`() {
            mockMvc
                .perform(delete("/api/v1/admin/companies/1").with(authOf(2L, "TEACHER")))
                .andExpect(status().isForbidden)

            verify(companyService, never()).delete(anyLong())
        }

        @Test
        fun `인증 없이 기업을 삭제하면 401 UNAUTHORIZED를 반환한다`() {
            mockMvc
                .perform(delete("/api/v1/admin/companies/1"))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        }
    }
