package team.inreok.getiserver.domain.inquiry.controller

import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.inquiry.dto.InquiryAssigneeResponse
import team.inreok.getiserver.domain.inquiry.dto.InquiryAuthorResponse
import team.inreok.getiserver.domain.inquiry.dto.InquiryCreateRequest
import team.inreok.getiserver.domain.inquiry.dto.InquiryCreateResponse
import team.inreok.getiserver.domain.inquiry.dto.InquiryDetailResponse
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryStatus
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryType
import team.inreok.getiserver.domain.inquiry.exception.InquiryAccessDeniedException
import team.inreok.getiserver.domain.inquiry.exception.InquiryNotFoundException
import team.inreok.getiserver.domain.inquiry.service.InquiryService
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig
import java.time.LocalDateTime

// SecurityConfig를 명시적으로 Import해 /api/v1/inquiries가 실제로 인증을 요구하는지(401)까지
// 검증한다(CompanyControllerTest와 동일한 방식). 상세 조회의 소유권 검증(403)은 Role이 아니라
// InquiryService 책임이므로, Service Mock이 InquiryAccessDeniedException을 던지게 해 Controller가
// 그 예외를 GlobalExceptionHandler를 통해 올바른 HTTP Status/ErrorCode로 변환하는지 검증한다.
@WebMvcTest(controllers = [InquiryController::class])
@Import(team.inreok.getiserver.global.security.NormalSecurityTestConfig::class)
@EnableWebSecurity
class InquiryControllerTest
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

        // Kotlin non-null 파라미터에 bare any()를 쓰면 null 반환으로 NPE가 나므로 Elvis로 기본값을
        // 채운다(ProgramAdminControllerTest.anyCreateRequest()와 동일한 이유).
        private fun anyCreateRequest(): InquiryCreateRequest =
            any(InquiryCreateRequest::class.java)
                ?: InquiryCreateRequest(inquiryType = InquiryType.ETC, title = "", content = "")

        private fun author() =
            InquiryAuthorResponse(
                memberId = 1L,
                name = "홍길동",
                profileImageUrl = null,
                cohort = 10,
                department = "SW_DEVELOPMENT",
                isPublic = true,
            )

        private fun createResponse() =
            InquiryCreateResponse(
                inquiryId = 1L,
                inquiryType = InquiryType.ERROR,
                title = "로그인이 안 됩니다",
                content = "로그인 버튼을 눌러도 반응이 없습니다.",
                status = InquiryStatus.RECEIVED,
                author = author(),
                files = emptyList(),
                answers = emptyList(),
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            )

        private fun detailResponse(assignee: InquiryAssigneeResponse? = null) =
            InquiryDetailResponse(
                inquiryId = 1L,
                inquiryType = InquiryType.ERROR,
                title = "로그인이 안 됩니다",
                content = "로그인 버튼을 눌러도 반응이 없습니다.",
                status = InquiryStatus.RECEIVED,
                author = author(),
                files = emptyList(),
                assignee = assignee,
                answers = emptyList(),
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            )

        // ------------------------------ POST /api/v1/inquiries ------------------------------

        @Test
        fun `학생은 문의를 등록할 수 있고 201을 반환한다`() {
            given(inquiryService.create(anyCreateRequest(), anyLong())).willReturn(createResponse())

            mockMvc
                .perform(
                    post("/api/v1/inquiries")
                        .with(authOf(1L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """{ "inquiryType": "ERROR", "title": "로그인이 안 됩니다", "content": "로그인 버튼을 눌러도 반응이 없습니다." }""",
                        ),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.data.status").value("RECEIVED"))
                .andExpect(jsonPath("$.meta.requestId").exists())
        }

        @Test
        fun `교사와 개발자도 문의를 등록할 수 있다`() {
            given(inquiryService.create(anyCreateRequest(), anyLong())).willReturn(createResponse())

            mockMvc
                .perform(
                    post("/api/v1/inquiries")
                        .with(authOf(2L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "inquiryType": "ETC", "title": "제목", "content": "내용" }"""),
                ).andExpect(status().isCreated)
        }

        @Test
        fun `제목이 비어 있으면 400 VALIDATION_FAILED를 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/inquiries")
                        .with(authOf(1L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "inquiryType": "ERROR", "title": "  ", "content": "내용" }"""),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))

            verify(inquiryService, never()).create(anyCreateRequest(), anyLong())
        }

        @Test
        fun `인증 없이 문의를 등록하면 401 UNAUTHORIZED를 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "inquiryType": "ERROR", "title": "제목", "content": "내용" }"""),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))

            verify(inquiryService, never()).create(anyCreateRequest(), anyLong())
        }

        // --------------------------- GET /api/v1/inquiries/{inquiryId} ---------------------------

        @Test
        fun `본인 문의는 상세 조회에 성공하고 200을 반환한다`() {
            given(inquiryService.getDetail(1L, 1L, false)).willReturn(detailResponse())

            mockMvc
                .perform(get("/api/v1/inquiries/1").with(authOf(1L, "STUDENT")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.inquiryId").value(1))
                .andExpect(jsonPath("$.data.assignee").doesNotExist())
        }

        @Test
        fun `개발자는 다른 사용자의 문의도 상세 조회할 수 있고 assignee가 노출된다`() {
            val assignee = InquiryAssigneeResponse(memberId = 5L, name = "김개발")
            given(inquiryService.getDetail(1L, 9L, true)).willReturn(detailResponse(assignee))

            mockMvc
                .perform(get("/api/v1/inquiries/1").with(authOf(9L, "DEVELOPER")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.assignee.memberId").value(5))
        }

        @Test
        fun `다른 사용자의 문의를 조회하면 403 INQUIRY_ACCESS_DENIED를 반환한다`() {
            given(inquiryService.getDetail(1L, 2L, false)).willThrow(InquiryAccessDeniedException())

            mockMvc
                .perform(get("/api/v1/inquiries/1").with(authOf(2L, "STUDENT")))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("INQUIRY_ACCESS_DENIED"))
        }

        @Test
        fun `존재하지 않는 문의를 조회하면 404 INQUIRY_NOT_FOUND를 반환한다`() {
            given(inquiryService.getDetail(999L, 1L, false)).willThrow(InquiryNotFoundException(999L))

            mockMvc
                .perform(get("/api/v1/inquiries/999").with(authOf(1L, "STUDENT")))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("INQUIRY_NOT_FOUND"))
        }

        @Test
        fun `인증 없이 상세를 조회하면 401 UNAUTHORIZED를 반환한다`() {
            mockMvc
                .perform(get("/api/v1/inquiries/1"))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        }
    }
