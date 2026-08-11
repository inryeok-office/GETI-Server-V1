package team.inreok.getiserver.domain.inquiry.controller

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.BDDMockito.given
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.Pageable
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.inquiry.dto.InquiryAdminListResponse
import team.inreok.getiserver.domain.inquiry.dto.InquiryAnswerCreateRequest
import team.inreok.getiserver.domain.inquiry.dto.InquiryAnswerCreateResponse
import team.inreok.getiserver.domain.inquiry.dto.InquiryAssigneeResponse
import team.inreok.getiserver.domain.inquiry.dto.InquiryAssigneeUpdateRequest
import team.inreok.getiserver.domain.inquiry.dto.InquiryAssigneeUpdateResponse
import team.inreok.getiserver.domain.inquiry.dto.InquiryStatusUpdateRequest
import team.inreok.getiserver.domain.inquiry.dto.InquiryStatusUpdateResponse
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryStatus
import team.inreok.getiserver.domain.inquiry.exception.InquiryAlreadyClosedException
import team.inreok.getiserver.domain.inquiry.exception.InquiryAssigneeMemberNotFoundException
import team.inreok.getiserver.domain.inquiry.exception.InquiryNotFoundException
import team.inreok.getiserver.domain.inquiry.exception.InquiryStatusInvalidException
import team.inreok.getiserver.domain.inquiry.exception.InvalidInquiryAssigneeException
import team.inreok.getiserver.domain.inquiry.service.InquiryService
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig
import team.inreok.getiserver.global.web.WebPageableConfig
import java.time.LocalDateTime

// SecurityConfig를 명시적으로 Import해 /api/v1/admin/inquiries 이하가 실제로 DEVELOPER 역할을
// 요구하는지(401/403)까지 검증한다(ProgramAdminControllerTest와 동일한 방식). Phase 3부터는 3개
// API 모두 미인증 401 / STUDENT 403 / TEACHER 403 / DEVELOPER 2xx 네 가지를 전부 검증한다.
// WebPageableConfig도 함께 Import해야 최대 Page Size(100) 강제가 이 Slice에서도 실제로 동작한다
// -- @WebMvcTest는 일반 @Configuration을 포함하지 않는다(NotificationControllerTest와 동일한 이유).
@WebMvcTest(controllers = [InquiryAdminController::class])
@Import(SecurityConfig::class, WebPageableConfig::class)
@EnableWebSecurity
class InquiryAdminControllerTest
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

        private fun anyAssigneeRequest(): InquiryAssigneeUpdateRequest =
            any(InquiryAssigneeUpdateRequest::class.java) ?: InquiryAssigneeUpdateRequest(assigneeId = null)

        private fun anyStatusRequest(): InquiryStatusUpdateRequest =
            any(InquiryStatusUpdateRequest::class.java) ?: InquiryStatusUpdateRequest(status = InquiryStatus.CLOSED)

        private fun anyAnswerRequest(): InquiryAnswerCreateRequest =
            any(InquiryAnswerCreateRequest::class.java) ?: InquiryAnswerCreateRequest(content = "")

        private fun answerResponse() =
            InquiryAnswerCreateResponse(
                answerId = 1L,
                inquiryId = 1L,
                authorMemberId = 9L,
                content = "확인 결과 서버 오류로 확인되어 수정했습니다.",
                files = emptyList(),
                createdAt = LocalDateTime.now(),
                inquiryStatus = InquiryStatus.ANSWERED,
                notificationCreated = true,
            )

        private fun listResponse() =
            InquiryAdminListResponse(
                content = emptyList(),
                page = 0,
                size = 20,
                totalElements = 0,
                totalPages = 0,
                first = true,
                last = true,
            )

        // ------------------------------ GET /api/v1/admin/inquiries ------------------------------

        @Test
        fun `개발자는 전체 문의 목록을 조회할 수 있다`() {
            given(
                inquiryService.listAdmin(
                    isNull(),
                    isNull(),
                    isNull(),
                    isNull(),
                    anyBoolean(),
                    anyLong(),
                    anyPageable(),
                ),
            ).willReturn(listResponse())

            mockMvc
                .perform(get("/api/v1/admin/inquiries").with(authOf(9L, "DEVELOPER")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.content").isArray)
        }

        @Test
        fun `학생은 전체 문의 목록을 조회할 수 없고 403을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/admin/inquiries").with(authOf(1L, "STUDENT")))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))

            verify(inquiryService, never())
                .listAdmin(any(), any(), any(), any(), anyBoolean(), anyLong(), anyPageable())
        }

        @Test
        fun `교사는 전체 문의 목록을 조회할 수 없고 403을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/admin/inquiries").with(authOf(2L, "TEACHER")))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))

            verify(inquiryService, never())
                .listAdmin(any(), any(), any(), any(), anyBoolean(), anyLong(), anyPageable())
        }

        @Test
        fun `인증 없이 전체 문의 목록을 조회하면 401 UNAUTHORIZED를 반환한다`() {
            mockMvc
                .perform(get("/api/v1/admin/inquiries"))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        }

        // page<0/size>100이 공통 Pagination 정책(WebPageableConfig)대로 보정되는지, 실제 Pageable
        // 해석 결과를 ArgumentCaptor로 확인한다 -- anyPageable()(Mockito any())만 쓰면 Service에
        // 어떤 값이 실제로 전달됐는지 검증하지 못한다(NotificationControllerTest와 동일한 방식).
        @Test
        fun `page가 음수이고 size가 최대값을 넘으면 0과 100으로 보정된다`() {
            given(
                inquiryService.listAdmin(
                    isNull(),
                    isNull(),
                    isNull(),
                    isNull(),
                    anyBoolean(),
                    anyLong(),
                    anyPageable(),
                ),
            ).willReturn(listResponse())

            mockMvc
                .perform(
                    get("/api/v1/admin/inquiries")
                        .param("page", "-1")
                        .param("size", "101")
                        .with(authOf(9L, "DEVELOPER")),
                ).andExpect(status().isOk)

            val pageableCaptor = ArgumentCaptor.forClass(Pageable::class.java)
            verify(inquiryService).listAdmin(
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                anyBoolean(),
                anyLong(),
                pageableCaptor.capture() ?: Pageable.unpaged(),
            )
            assertThat(pageableCaptor.value.pageNumber).isEqualTo(0)
            assertThat(pageableCaptor.value.pageSize).isEqualTo(100)
        }

        // -------------------------- PATCH /api/v1/admin/inquiries/{id}/assignee --------------------------

        @Test
        fun `개발자는 담당자를 지정할 수 있다`() {
            val response =
                InquiryAssigneeUpdateResponse(
                    inquiryId = 1L,
                    assignee = InquiryAssigneeResponse(memberId = 5L, name = "김개발"),
                    updatedAt = LocalDateTime.now(),
                )
            given(inquiryService.updateAssignee(eq(1L), anyAssigneeRequest())).willReturn(response)

            mockMvc
                .perform(
                    patch("/api/v1/admin/inquiries/1/assignee")
                        .with(authOf(9L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "assigneeId": 5 }"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.assignee.memberId").value(5))
        }

        @Test
        fun `assigneeId를 null로 보내면 담당자가 해제되고 assignee가 null로 응답된다`() {
            val response =
                InquiryAssigneeUpdateResponse(inquiryId = 1L, assignee = null, updatedAt = LocalDateTime.now())
            given(inquiryService.updateAssignee(eq(1L), anyAssigneeRequest())).willReturn(response)

            mockMvc
                .perform(
                    patch("/api/v1/admin/inquiries/1/assignee")
                        .with(authOf(9L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "assigneeId": null }"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.assignee").doesNotExist())
        }

        @Test
        fun `role 또는 status 조건을 만족하지 않는 회원을 지정하면 400 INVALID_INQUIRY_ASSIGNEE를 반환한다`() {
            given(inquiryService.updateAssignee(eq(1L), anyAssigneeRequest()))
                .willThrow(InvalidInquiryAssigneeException())

            mockMvc
                .perform(
                    patch("/api/v1/admin/inquiries/1/assignee")
                        .with(authOf(9L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "assigneeId": 2 }"""),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("INVALID_INQUIRY_ASSIGNEE"))
        }

        @Test
        fun `존재하지 않는 회원을 지정하면 404 MEMBER_NOT_FOUND를 반환한다`() {
            given(inquiryService.updateAssignee(eq(1L), anyAssigneeRequest()))
                .willThrow(InquiryAssigneeMemberNotFoundException(999L))

            mockMvc
                .perform(
                    patch("/api/v1/admin/inquiries/1/assignee")
                        .with(authOf(9L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "assigneeId": 999 }"""),
                ).andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("MEMBER_NOT_FOUND"))
        }

        @Test
        fun `존재하지 않는 문의에 담당자를 지정하면 404 INQUIRY_NOT_FOUND를 반환한다`() {
            given(inquiryService.updateAssignee(eq(999L), anyAssigneeRequest()))
                .willThrow(InquiryNotFoundException(999L))

            mockMvc
                .perform(
                    patch("/api/v1/admin/inquiries/999/assignee")
                        .with(authOf(9L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "assigneeId": 5 }"""),
                ).andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("INQUIRY_NOT_FOUND"))
        }

        @Test
        fun `학생은 담당자를 지정할 수 없고 403을 반환한다`() {
            mockMvc
                .perform(
                    patch("/api/v1/admin/inquiries/1/assignee")
                        .with(authOf(1L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "assigneeId": 5 }"""),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))

            verify(inquiryService, never()).updateAssignee(anyLong(), anyAssigneeRequest())
        }

        @Test
        fun `교사는 담당자를 지정할 수 없고 403을 반환한다`() {
            mockMvc
                .perform(
                    patch("/api/v1/admin/inquiries/1/assignee")
                        .with(authOf(2L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "assigneeId": 5 }"""),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))

            verify(inquiryService, never()).updateAssignee(anyLong(), anyAssigneeRequest())
        }

        @Test
        fun `인증 없이 담당자를 지정하면 401 UNAUTHORIZED를 반환한다`() {
            mockMvc
                .perform(
                    patch("/api/v1/admin/inquiries/1/assignee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "assigneeId": 5 }"""),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        }

        // -------------------------- PATCH /api/v1/admin/inquiries/{id}/status --------------------------

        @Test
        fun `개발자는 상태를 변경할 수 있다`() {
            val response =
                InquiryStatusUpdateResponse(
                    inquiryId = 1L,
                    status = InquiryStatus.IN_PROGRESS,
                    updatedAt = LocalDateTime.now(),
                )
            given(inquiryService.changeStatus(eq(1L), anyStatusRequest())).willReturn(response)

            mockMvc
                .perform(
                    patch("/api/v1/admin/inquiries/1/status")
                        .with(authOf(9L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "status": "IN_PROGRESS" }"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
        }

        // status는 Request Body 필드라 잘못된 값은 Query Parameter(TYPE_MISMATCH)와 달리 JSON
        // 역직렬화 실패(HttpMessageNotReadableException)로 이어져 MALFORMED_JSON이 된다
        // (GlobalExceptionHandlerTest의 두 시나리오 구분과 동일한 이유).
        @Test
        fun `허용되지 않은 status 값을 보내면 400 MALFORMED_JSON을 반환한다`() {
            mockMvc
                .perform(
                    patch("/api/v1/admin/inquiries/1/status")
                        .with(authOf(9L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "status": "BOGUS" }"""),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("MALFORMED_JSON"))

            verify(inquiryService, never()).changeStatus(anyLong(), anyStatusRequest())
        }

        @Test
        fun `허용되지 않은 상태 전이는 400 INQUIRY_STATUS_INVALID를 반환한다`() {
            given(inquiryService.changeStatus(eq(1L), anyStatusRequest()))
                .willThrow(InquiryStatusInvalidException("허용되지 않은 상태 전이입니다. (CLOSED -> IN_PROGRESS)"))

            mockMvc
                .perform(
                    patch("/api/v1/admin/inquiries/1/status")
                        .with(authOf(9L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "status": "IN_PROGRESS" }"""),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("INQUIRY_STATUS_INVALID"))
        }

        @Test
        fun `답변이 없는 문의를 ANSWERED로 직접 변경하면 400 INQUIRY_STATUS_INVALID를 반환한다`() {
            given(inquiryService.changeStatus(eq(1L), anyStatusRequest()))
                .willThrow(InquiryStatusInvalidException("답변이 없는 문의는 ANSWERED로 변경할 수 없습니다."))

            mockMvc
                .perform(
                    patch("/api/v1/admin/inquiries/1/status")
                        .with(authOf(9L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "status": "ANSWERED" }"""),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("INQUIRY_STATUS_INVALID"))
        }

        @Test
        fun `존재하지 않는 문의의 상태를 변경하면 404 INQUIRY_NOT_FOUND를 반환한다`() {
            given(inquiryService.changeStatus(eq(999L), anyStatusRequest()))
                .willThrow(InquiryNotFoundException(999L))

            mockMvc
                .perform(
                    patch("/api/v1/admin/inquiries/999/status")
                        .with(authOf(9L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "status": "CLOSED" }"""),
                ).andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("INQUIRY_NOT_FOUND"))
        }

        @Test
        fun `학생은 상태를 변경할 수 없고 403을 반환한다`() {
            mockMvc
                .perform(
                    patch("/api/v1/admin/inquiries/1/status")
                        .with(authOf(1L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "status": "CLOSED" }"""),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))

            verify(inquiryService, never()).changeStatus(anyLong(), anyStatusRequest())
        }

        @Test
        fun `교사는 상태를 변경할 수 없고 403을 반환한다`() {
            mockMvc
                .perform(
                    patch("/api/v1/admin/inquiries/1/status")
                        .with(authOf(2L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "status": "CLOSED" }"""),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))

            verify(inquiryService, never()).changeStatus(anyLong(), anyStatusRequest())
        }

        @Test
        fun `인증 없이 상태를 변경하면 401 UNAUTHORIZED를 반환한다`() {
            mockMvc
                .perform(
                    patch("/api/v1/admin/inquiries/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "status": "CLOSED" }"""),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        }

        // -------------------------- POST /api/v1/admin/inquiries/{id}/answers --------------------------

        @Test
        fun `개발자는 답변을 등록할 수 있고 201과 ANSWERED로 전이된 상태를 반환한다`() {
            given(inquiryService.createAnswer(eq(1L), anyAnswerRequest(), anyLong())).willReturn(answerResponse())

            mockMvc
                .perform(
                    post("/api/v1/admin/inquiries/1/answers")
                        .with(authOf(9L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "content": "확인 결과 서버 오류로 확인되어 수정했습니다." }"""),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.data.inquiryStatus").value("ANSWERED"))
                .andExpect(jsonPath("$.data.notificationCreated").value(true))
        }

        // Validation 순서 회귀 고정(사용자 확인 완료) -- content blank(400)가 CLOSED(409)보다
        // 먼저 나와야 한다. Service Mock을 일부러 409로 응답하도록 준비해도, @Valid가 Controller
        // Method 진입 자체를 막으므로 Service는 호출되지 않고 항상 400이 반환돼야 한다.
        @Test
        fun `답변 내용이 비어 있으면 CLOSED 여부와 무관하게 400 VALIDATION_FAILED를 반환한다`() {
            given(inquiryService.createAnswer(eq(1L), anyAnswerRequest(), anyLong()))
                .willThrow(InquiryAlreadyClosedException(1L))

            mockMvc
                .perform(
                    post("/api/v1/admin/inquiries/1/answers")
                        .with(authOf(9L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "content": "  " }"""),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))

            verify(inquiryService, never()).createAnswer(anyLong(), anyAnswerRequest(), anyLong())
        }

        @Test
        fun `이미 CLOSED된 문의에 답변을 등록하면 409 INQUIRY_ALREADY_CLOSED를 반환한다`() {
            given(inquiryService.createAnswer(eq(1L), anyAnswerRequest(), anyLong()))
                .willThrow(InquiryAlreadyClosedException(1L))

            mockMvc
                .perform(
                    post("/api/v1/admin/inquiries/1/answers")
                        .with(authOf(9L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "content": "답변 내용" }"""),
                ).andExpect(status().isConflict)
                .andExpect(jsonPath("$.error.code").value("INQUIRY_ALREADY_CLOSED"))
        }

        @Test
        fun `존재하지 않는 문의에 답변을 등록하면 404 INQUIRY_NOT_FOUND를 반환한다`() {
            given(inquiryService.createAnswer(eq(999L), anyAnswerRequest(), anyLong()))
                .willThrow(InquiryNotFoundException(999L))

            mockMvc
                .perform(
                    post("/api/v1/admin/inquiries/999/answers")
                        .with(authOf(9L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "content": "답변 내용" }"""),
                ).andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("INQUIRY_NOT_FOUND"))
        }

        @Test
        fun `학생은 답변을 등록할 수 없고 403을 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/admin/inquiries/1/answers")
                        .with(authOf(1L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "content": "답변 내용" }"""),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))

            verify(inquiryService, never()).createAnswer(anyLong(), anyAnswerRequest(), anyLong())
        }

        @Test
        fun `교사는 답변을 등록할 수 없고 403을 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/admin/inquiries/1/answers")
                        .with(authOf(2L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "content": "답변 내용" }"""),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))

            verify(inquiryService, never()).createAnswer(anyLong(), anyAnswerRequest(), anyLong())
        }

        @Test
        fun `인증 없이 답변을 등록하면 401 UNAUTHORIZED를 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/admin/inquiries/1/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "content": "답변 내용" }"""),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        }
    }
