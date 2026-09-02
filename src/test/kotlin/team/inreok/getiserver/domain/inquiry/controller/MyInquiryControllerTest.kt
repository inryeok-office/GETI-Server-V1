package team.inreok.getiserver.domain.inquiry.controller

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.BDDMockito.given
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
import team.inreok.getiserver.domain.inquiry.dto.InquiryListItemResponse
import team.inreok.getiserver.domain.inquiry.dto.InquiryListResponse
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryStatus
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryType
import team.inreok.getiserver.domain.inquiry.service.InquiryService
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig
import team.inreok.getiserver.global.web.WebPageableConfig
import java.time.LocalDateTime

// SecurityConfig를 명시적으로 Import해 /api/v1/me/inquiries가 실제로 인증을 요구하는지(401)까지
// 검증한다. SecurityConfig가 이 경로를 명시적인 Rule로 선언하므로(MyInquiryController Class 주석
// 참고), 이 Test는 그 Rule이 실제로 적용됨을 확인한다. WebPageableConfig도 함께 Import해야 최대
// Page Size(100) 강제가 이 Slice에서도 실제로 동작한다 -- @WebMvcTest는 일반 @Configuration을
// 포함하지 않는다(NotificationControllerTest와 동일한 이유).
@WebMvcTest(controllers = [MyInquiryController::class])
@Import(team.inreok.getiserver.global.security.NormalSecurityTestConfig::class, WebPageableConfig::class)
@EnableWebSecurity
class MyInquiryControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var inquiryService: InquiryService

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

        // Kotlin non-null 파라미터(Pageable)에 bare any()를 쓰면 null 반환으로 NPE가 나므로 Elvis로
        // 기본값을 채운다(InquiryControllerTest.anyCreateRequest()와 동일한 이유).
        private fun anyPageable(): Pageable = any(Pageable::class.java) ?: Pageable.unpaged()

        private fun listResponse() =
            InquiryListResponse(
                content =
                    listOf(
                        InquiryListItemResponse(
                            inquiryId = 1L,
                            inquiryType = InquiryType.ERROR,
                            title = "로그인이 안 됩니다",
                            status = InquiryStatus.RECEIVED,
                            createdAt = LocalDateTime.now(),
                            answeredAt = null,
                        ),
                    ),
                page = 0,
                size = 20,
                totalElements = 1,
                totalPages = 1,
                first = true,
                last = true,
            )

        @Test
        fun `학생은 본인 문의 목록을 조회할 수 있다`() {
            given(inquiryService.listMine(isNull(), anyLong(), anyPageable())).willReturn(listResponse())

            mockMvc
                .perform(get("/api/v1/me/inquiries").with(authOf(1L, "STUDENT")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.content[0].inquiryId").value(1))
                .andExpect(jsonPath("$.data.content[0].status").value("RECEIVED"))
        }

        @Test
        fun `status 필터를 지정해 조회할 수 있다`() {
            given(inquiryService.listMine(eq(InquiryStatus.ANSWERED), anyLong(), anyPageable()))
                .willReturn(listResponse())

            mockMvc
                .perform(get("/api/v1/me/inquiries?status=ANSWERED").with(authOf(1L, "STUDENT")))
                .andExpect(status().isOk)
        }

        @Test
        fun `교사와 개발자도 내 문의 목록을 조회할 수 있다`() {
            given(inquiryService.listMine(isNull(), anyLong(), anyPageable())).willReturn(listResponse())

            mockMvc
                .perform(get("/api/v1/me/inquiries").with(authOf(2L, "TEACHER")))
                .andExpect(status().isOk)
        }

        @Test
        fun `인증 없이 내 문의 목록을 조회하면 401 UNAUTHORIZED를 반환한다`() {
            mockMvc
                .perform(get("/api/v1/me/inquiries"))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        }

        @Test
        fun `허용되지 않은 status 값을 보내면 400 TYPE_MISMATCH를 반환한다`() {
            mockMvc
                .perform(get("/api/v1/me/inquiries").param("status", "BOGUS").with(authOf(1L, "STUDENT")))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("TYPE_MISMATCH"))
        }

        // page<0/size>100이 공통 Pagination 정책(WebPageableConfig)대로 보정되는지, 실제 Pageable
        // 해석 결과를 ArgumentCaptor로 확인한다(InquiryAdminControllerTest와 동일한 방식).
        @Test
        fun `page가 음수이고 size가 최대값을 넘으면 0과 100으로 보정된다`() {
            given(inquiryService.listMine(isNull(), anyLong(), anyPageable())).willReturn(listResponse())

            mockMvc
                .perform(
                    get("/api/v1/me/inquiries")
                        .param("page", "-1")
                        .param("size", "101")
                        .with(authOf(1L, "STUDENT")),
                ).andExpect(status().isOk)

            val pageableCaptor = ArgumentCaptor.forClass(Pageable::class.java)
            verify(inquiryService).listMine(isNull(), anyLong(), pageableCaptor.capture() ?: Pageable.unpaged())
            assertThat(pageableCaptor.value.pageNumber).isEqualTo(0)
            assertThat(pageableCaptor.value.pageSize).isEqualTo(100)
        }
    }
