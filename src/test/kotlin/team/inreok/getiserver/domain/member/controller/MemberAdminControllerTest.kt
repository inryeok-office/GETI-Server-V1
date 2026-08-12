package team.inreok.getiserver.domain.member.controller

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.member.dto.ApprovalAction
import team.inreok.getiserver.domain.member.dto.MemberApprovalRequest
import team.inreok.getiserver.domain.member.dto.MemberApprovalResponse
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.exception.MemberNotApprovalTargetException
import team.inreok.getiserver.domain.member.exception.MemberNotFoundException
import team.inreok.getiserver.domain.member.exception.MemberNotPendingException
import team.inreok.getiserver.domain.member.service.MemberApprovalService
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig
import java.time.LocalDateTime

// SecurityConfig를 명시적으로 Import해 /api/v1/admin/members가 실제로 DEVELOPER 권한을 요구하는지
// (401/403)까지 검증한다.
@WebMvcTest(controllers = [MemberAdminController::class])
@Import(SecurityConfig::class)
@EnableWebSecurity
class MemberAdminControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var memberApprovalService: MemberApprovalService

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

        // Kotlin non-null 파라미터에 bare any()를 쓰면 null 반환으로 NPE가 나므로 non-null Fallback을 둔다.
        private fun anyRequest(): MemberApprovalRequest =
            any(MemberApprovalRequest::class.java) ?: MemberApprovalRequest(action = ApprovalAction.APPROVE)

        private fun response(
            memberId: Long = 42L,
            status: MemberStatus = MemberStatus.ACTIVE,
            reason: String? = null,
        ) = MemberApprovalResponse(
            memberId = memberId,
            status = status,
            reason = reason,
            processedAt = LocalDateTime.of(2026, 8, 13, 9, 30),
        )

        // --- 승인/거절 성공 ---

        @Test
        fun `개발자가 승인하면 200과 ACTIVE를 반환한다`() {
            given(memberApprovalService.process(anyLong(), anyRequest())).willReturn(response())

            mockMvc
                .perform(
                    post("/api/v1/admin/members/42/approval-actions")
                        .with(authOf(1L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"action":"APPROVE"}"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.memberId").value(42))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.reason").doesNotExist())
        }

        @Test
        fun `개발자가 거절하면 200과 REJECTED, 사유를 반환한다`() {
            given(memberApprovalService.process(anyLong(), anyRequest()))
                .willReturn(response(status = MemberStatus.REJECTED, reason = "자료 미확인"))

            mockMvc
                .perform(
                    post("/api/v1/admin/members/42/approval-actions")
                        .with(authOf(1L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"action":"REJECT","reason":"자료 미확인"}"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.reason").value("자료 미확인"))
        }

        // --- 권한 ---

        @Test
        fun `인증 없이 요청하면 401을 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/admin/members/42/approval-actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"action":"APPROVE"}"""),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))

            verify(memberApprovalService, never()).process(anyLong(), anyRequest())
        }

        @Test
        fun `학생이 요청하면 403을 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/admin/members/42/approval-actions")
                        .with(authOf(1L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"action":"APPROVE"}"""),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))

            verify(memberApprovalService, never()).process(anyLong(), anyRequest())
        }

        @Test
        fun `교사가 요청하면 403을 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/admin/members/42/approval-actions")
                        .with(authOf(1L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"action":"APPROVE"}"""),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))

            verify(memberApprovalService, never()).process(anyLong(), anyRequest())
        }

        // --- 오류 계약 ---

        @Test
        fun `없는 회원이면 404와 MEMBER_NOT_FOUND를 반환한다`() {
            willThrow(MemberNotFoundException(999L)).given(memberApprovalService).process(anyLong(), anyRequest())

            mockMvc
                .perform(
                    post("/api/v1/admin/members/999/approval-actions")
                        .with(authOf(1L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"action":"APPROVE"}"""),
                ).andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("MEMBER_NOT_FOUND"))
        }

        @Test
        fun `PENDING이 아니면 409와 MEMBER_NOT_PENDING을 반환한다`() {
            willThrow(MemberNotPendingException(MemberStatus.ACTIVE))
                .given(memberApprovalService)
                .process(anyLong(), anyRequest())

            mockMvc
                .perform(
                    post("/api/v1/admin/members/42/approval-actions")
                        .with(authOf(1L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"action":"APPROVE"}"""),
                ).andExpect(status().isConflict)
                .andExpect(jsonPath("$.error.code").value("MEMBER_NOT_PENDING"))
        }

        @Test
        fun `교직원 승인 대상이 아니면 400과 MEMBER_NOT_APPROVAL_TARGET을 반환한다`() {
            willThrow(MemberNotApprovalTargetException(OAuthProvider.DG))
                .given(memberApprovalService)
                .process(anyLong(), anyRequest())

            mockMvc
                .perform(
                    post("/api/v1/admin/members/42/approval-actions")
                        .with(authOf(1L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"action":"APPROVE"}"""),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("MEMBER_NOT_APPROVAL_TARGET"))
        }

        @Test
        fun `거절 사유가 1000자를 넘으면 400과 VALIDATION_FAILED를 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/admin/members/42/approval-actions")
                        .with(authOf(1L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"action":"REJECT","reason":"${"가".repeat(1001)}"}"""),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))

            verify(memberApprovalService, never()).process(anyLong(), anyRequest())
        }

        @Test
        fun `잘못된 action 값이면 400을 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/admin/members/42/approval-actions")
                        .with(authOf(1L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"action":"SUSPEND"}"""),
                ).andExpect(status().isBadRequest)

            verify(memberApprovalService, never()).process(anyLong(), anyRequest())
        }
    }
