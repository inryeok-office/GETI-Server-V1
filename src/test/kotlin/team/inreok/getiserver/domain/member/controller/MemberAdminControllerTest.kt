package team.inreok.getiserver.domain.member.controller

import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anySet
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
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
import team.inreok.getiserver.domain.member.dto.AdminMemberDetailResponse
import team.inreok.getiserver.domain.member.dto.AdminMemberSearchItemResponse
import team.inreok.getiserver.domain.member.dto.AdminMemberSearchResponse
import team.inreok.getiserver.domain.member.dto.ApprovalAction
import team.inreok.getiserver.domain.member.dto.MemberApprovalRequest
import team.inreok.getiserver.domain.member.dto.MemberApprovalResponse
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.exception.MemberNotApprovalTargetException
import team.inreok.getiserver.domain.member.exception.MemberNotFoundException
import team.inreok.getiserver.domain.member.exception.MemberNotPendingException
import team.inreok.getiserver.domain.member.exception.MemberSelfModificationForbiddenException
import team.inreok.getiserver.domain.member.exception.MemberStatusTransitionNotAllowedException
import team.inreok.getiserver.domain.member.service.MemberAdminManagementService
import team.inreok.getiserver.domain.member.service.MemberApprovalService
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig
import java.time.LocalDateTime

// SecurityConfig를 명시적으로 Import해 /api/v1/admin/members가 실제로 DEVELOPER 권한을 요구하는지
// (401/403)까지 검증한다.
@WebMvcTest(controllers = [MemberAdminController::class])
@Import(team.inreok.getiserver.global.security.NormalSecurityTestConfig::class)
@EnableWebSecurity
class MemberAdminControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var memberApprovalService: MemberApprovalService

        @MockitoBean
        private lateinit var memberAdminManagementService: MemberAdminManagementService

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

        private fun anyPageable(): Pageable = any(Pageable::class.java) ?: Pageable.unpaged()

        private fun detailResponse(
            memberId: Long = 42L,
            status: MemberStatus = MemberStatus.ACTIVE,
            roles: Set<RoleType> = setOf(RoleType.STUDENT),
        ) = AdminMemberDetailResponse(
            memberId = memberId,
            email = "member-$memberId@example.com",
            name = "회원$memberId",
            status = status,
            roles = roles,
            oauthProvider = OAuthProvider.DG,
            academicStatus = null,
            cohort = null,
            grade = null,
            department = null,
            phoneNumber = null,
            githubUrl = null,
            rejectionReason = null,
            approvedAt = null,
            createdAt = LocalDateTime.of(2026, 8, 13, 9, 30),
            updatedAt = LocalDateTime.of(2026, 8, 13, 9, 30),
            withdrawnAt = null,
        )

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

        // --- 회원 목록 검색(GET /search) ---

        @Test
        fun `개발자가 목록을 조회하면 200과 결과를 반환한다`() {
            val response =
                AdminMemberSearchResponse(
                    content =
                        listOf(
                            AdminMemberSearchItemResponse(
                                memberId = 42L,
                                email = "member-42@example.com",
                                name = "김학생",
                                status = MemberStatus.ACTIVE,
                                roles = setOf(RoleType.STUDENT),
                                oauthProvider = OAuthProvider.DG,
                                cohort = 5,
                                department = null,
                                createdAt = LocalDateTime.of(2026, 8, 13, 9, 30),
                            ),
                        ),
                    page = 0,
                    size = 20,
                    totalElements = 1,
                    totalPages = 1,
                    first = true,
                    last = true,
                )
            // Mockito는 한 호출에서 Matcher를 하나라도 쓰면 모든 Argument를 Matcher로 줘야
            // 한다(InvalidUseOfMatchersException 방지) -- pageable만 anyPageable()을 쓰고 나머지를
            // 리터럴 null로 두면 안 된다. Nullable Parameter는 any()가 null도 매칭한다.
            given(
                memberAdminManagementService.search(any(), any(), any(), any(), any(), anyPageable()),
            ).willReturn(response)

            mockMvc
                .perform(get("/api/v1/admin/members/search").with(authOf(1L, "DEVELOPER")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.content[0].memberId").value(42))
                .andExpect(jsonPath("$.data.content[0].email").value("member-42@example.com"))
        }

        @Test
        fun `교사가 목록 조회를 요청하면 403을 반환한다`() {
            // Issue #183 -- 이 Endpoint는 DEVELOPER 전용이다(TEACHER도 접근 가능한 Dropdown 조회
            // GET /api/v1/admin/members(Issue #182)와는 별개 Endpoint).
            mockMvc
                .perform(get("/api/v1/admin/members/search").with(authOf(1L, "TEACHER")))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
        }

        @Test
        fun `인증 없이 목록을 조회하면 401을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/admin/members/search"))
                .andExpect(status().isUnauthorized)
        }

        // --- 회원 상세 조회(GET /{memberId}) ---

        @Test
        fun `개발자가 상세를 조회하면 200과 email GitHub URL 전화번호를 포함한 결과를 반환한다`() {
            given(memberAdminManagementService.getDetail(42L))
                .willReturn(
                    detailResponse().copy(phoneNumber = "010-1234-5678", githubUrl = "https://github.com/example"),
                )

            mockMvc
                .perform(get("/api/v1/admin/members/42").with(authOf(1L, "DEVELOPER")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.memberId").value(42))
                .andExpect(jsonPath("$.data.phoneNumber").value("010-1234-5678"))
                .andExpect(jsonPath("$.data.githubUrl").value("https://github.com/example"))
        }

        @Test
        fun `존재하지 않는 회원을 상세 조회하면 404와 MEMBER_NOT_FOUND를 반환한다`() {
            willThrow(MemberNotFoundException(999L)).given(memberAdminManagementService).getDetail(999L)

            mockMvc
                .perform(get("/api/v1/admin/members/999").with(authOf(1L, "DEVELOPER")))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("MEMBER_NOT_FOUND"))
        }

        // --- Role Set 교체(PATCH /{memberId}/roles) ---

        @Test
        fun `개발자가 다른 회원의 Role을 교체하면 200을 반환한다`() {
            given(memberAdminManagementService.updateRoles(42L, 1L, setOf(RoleType.TEACHER)))
                .willReturn(detailResponse(roles = setOf(RoleType.TEACHER)))

            mockMvc
                .perform(
                    patch("/api/v1/admin/members/42/roles")
                        .with(authOf(1L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"roles":["TEACHER"]}"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.roles[0]").value("TEACHER"))
        }

        @Test
        fun `자기 자신의 Role을 바꾸려 하면 403과 MEMBER_SELF_MODIFICATION_FORBIDDEN을 반환한다`() {
            willThrow(MemberSelfModificationForbiddenException())
                .given(memberAdminManagementService)
                .updateRoles(1L, 1L, setOf(RoleType.TEACHER))

            mockMvc
                .perform(
                    patch("/api/v1/admin/members/1/roles")
                        .with(authOf(1L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"roles":["TEACHER"]}"""),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("MEMBER_SELF_MODIFICATION_FORBIDDEN"))
        }

        @Test
        fun `Role 교체 대상 회원이 없으면 404와 MEMBER_NOT_FOUND를 반환한다`() {
            willThrow(MemberNotFoundException(999L))
                .given(memberAdminManagementService)
                .updateRoles(999L, 1L, setOf(RoleType.TEACHER))

            mockMvc
                .perform(
                    patch("/api/v1/admin/members/999/roles")
                        .with(authOf(1L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"roles":["TEACHER"]}"""),
                ).andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("MEMBER_NOT_FOUND"))
        }

        @Test
        fun `roles 필드가 없으면 400을 반환한다`() {
            mockMvc
                .perform(
                    patch("/api/v1/admin/members/42/roles")
                        .with(authOf(1L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{}"""),
                ).andExpect(status().isBadRequest)

            verify(memberAdminManagementService, never()).updateRoles(anyLong(), anyLong(), anySet())
        }

        // --- 계정 상태 변경(PATCH /{memberId}/status) ---

        @Test
        fun `개발자가 다른 회원을 SUSPENDED로 변경하면 200을 반환한다`() {
            given(memberAdminManagementService.updateStatus(42L, 1L, MemberStatus.SUSPENDED))
                .willReturn(detailResponse(status = MemberStatus.SUSPENDED))

            mockMvc
                .perform(
                    patch("/api/v1/admin/members/42/status")
                        .with(authOf(1L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"status":"SUSPENDED"}"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"))
        }

        @Test
        fun `자기 자신의 상태를 바꾸려 하면 403과 MEMBER_SELF_MODIFICATION_FORBIDDEN을 반환한다`() {
            willThrow(MemberSelfModificationForbiddenException())
                .given(memberAdminManagementService)
                .updateStatus(1L, 1L, MemberStatus.SUSPENDED)

            mockMvc
                .perform(
                    patch("/api/v1/admin/members/1/status")
                        .with(authOf(1L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"status":"SUSPENDED"}"""),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("MEMBER_SELF_MODIFICATION_FORBIDDEN"))
        }

        @Test
        fun `허용되지 않는 상태 전이를 요청하면 409와 MEMBER_STATUS_TRANSITION_NOT_ALLOWED를 반환한다`() {
            willThrow(MemberStatusTransitionNotAllowedException(MemberStatus.PENDING, MemberStatus.ACTIVE))
                .given(memberAdminManagementService)
                .updateStatus(42L, 1L, MemberStatus.ACTIVE)

            mockMvc
                .perform(
                    patch("/api/v1/admin/members/42/status")
                        .with(authOf(1L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"status":"ACTIVE"}"""),
                ).andExpect(status().isConflict)
                .andExpect(jsonPath("$.error.code").value("MEMBER_STATUS_TRANSITION_NOT_ALLOWED"))
        }

        @Test
        fun `상태 변경 대상 회원이 없으면 404와 MEMBER_NOT_FOUND를 반환한다`() {
            willThrow(MemberNotFoundException(999L))
                .given(memberAdminManagementService)
                .updateStatus(999L, 1L, MemberStatus.SUSPENDED)

            mockMvc
                .perform(
                    patch("/api/v1/admin/members/999/status")
                        .with(authOf(1L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"status":"SUSPENDED"}"""),
                ).andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("MEMBER_NOT_FOUND"))
        }

        @Test
        fun `status 필드가 없으면 400을 반환한다`() {
            mockMvc
                .perform(
                    patch("/api/v1/admin/members/42/status")
                        .with(authOf(1L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{}"""),
                ).andExpect(status().isBadRequest)

            verify(memberAdminManagementService, never())
                .updateStatus(anyLong(), anyLong(), any(MemberStatus::class.java) ?: MemberStatus.ACTIVE)
        }
    }
