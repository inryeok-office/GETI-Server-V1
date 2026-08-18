package team.inreok.getiserver.domain.program.controller

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
import team.inreok.getiserver.domain.program.dto.ProgramApplicationActionRequest
import team.inreok.getiserver.domain.program.dto.ProgramApplicationActionResponse
import team.inreok.getiserver.domain.program.dto.ProgramDetailResponse
import team.inreok.getiserver.domain.program.dto.ProgramFileResponse
import team.inreok.getiserver.domain.program.entity.type.ProgramApplicationAction
import team.inreok.getiserver.domain.program.entity.type.ProgramApplicationEligibilityReason
import team.inreok.getiserver.domain.program.entity.type.ProgramApplicationStatus
import team.inreok.getiserver.domain.program.entity.type.ProgramStatus
import team.inreok.getiserver.domain.program.entity.type.ProgramType
import team.inreok.getiserver.domain.program.service.ProgramService
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig
import java.time.LocalDateTime

// SecurityConfig를 명시적으로 Import해 POST /api/v1/programs/{programId}/application-actions가
// 실제로 STUDENT 권한을 요구하는지(401/403)까지 검증한다(CompanyAdminControllerTest와 동일한 방식).
@WebMvcTest(controllers = [ProgramController::class])
@Import(team.inreok.getiserver.global.security.NormalSecurityTestConfig::class)
@EnableWebSecurity
class ProgramControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var programService: ProgramService

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
        // 채운다(CompanyAdminControllerTest.anyCreateRequest()와 동일한 이유).
        private fun anyActionRequest(): ProgramApplicationActionRequest =
            any(ProgramApplicationActionRequest::class.java)
                ?: ProgramApplicationActionRequest(action = ProgramApplicationAction.APPLY)

        private val applyResponse =
            ProgramApplicationActionResponse(
                applicationId = 1L,
                programId = 1L,
                status = ProgramApplicationStatus.APPLIED,
                currentApplicants = 1,
                remainingCapacity = 19,
                availableActions = listOf("CANCEL"),
                vacancyNotificationCount = 0,
                updatedAt = LocalDateTime.now(),
            )

        @Test
        fun `학생은 프로그램을 신청할 수 있다`() {
            given(programService.executeApplicationAction(anyLong(), anyLong(), anyActionRequest()))
                .willReturn(applyResponse)

            mockMvc
                .perform(
                    post("/api/v1/programs/1/application-actions")
                        .with(authOf(1L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "action": "APPLY" }"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.status").value("APPLIED"))
        }

        @Test
        fun `교사는 프로그램을 신청할 수 없고 403을 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/programs/1/application-actions")
                        .with(authOf(2L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "action": "APPLY" }"""),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))

            verify(programService, never()).executeApplicationAction(anyLong(), anyLong(), anyActionRequest())
        }

        @Test
        fun `개발자는 프로그램을 신청할 수 없고 403을 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/programs/1/application-actions")
                        .with(authOf(3L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "action": "APPLY" }"""),
                ).andExpect(status().isForbidden)

            verify(programService, never()).executeApplicationAction(anyLong(), anyLong(), anyActionRequest())
        }

        @Test
        fun `인증 없이 신청하면 401 UNAUTHORIZED를 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/programs/1/application-actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "action": "APPLY" }"""),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        }

        private fun detailResponseWithFiles() =
            ProgramDetailResponse(
                programId = 1L,
                title = "특강",
                content = "본문",
                programType = ProgramType.SPECIAL_LECTURE,
                targetGrades = listOf(2, 3),
                startAt = null,
                endAt = null,
                applicationStartAt = null,
                applicationEndAt = null,
                capacity = null,
                currentApplicants = 0,
                remainingCapacity = null,
                firstComeServed = true,
                canApply = false,
                eligibilityReason = ProgramApplicationEligibilityReason.NOT_ENROLLED,
                eligibilityMessage = "재학 중인 학생만 신청할 수 있습니다.",
                availableActions = emptyList(),
                canSubscribeVacancy = false,
                vacancySubscribed = false,
                vacancySubscriptionStatus = null,
                status = ProgramStatus.DRAFT,
                files =
                    listOf(
                        ProgramFileResponse(
                            fileId = 5L,
                            originalName = "안내문.pdf",
                            contentType = "application/pdf",
                            size = 1024L,
                            downloadUrl = "/api/v1/files/5/download",
                        ),
                    ),
            )

        // Controller는 Role을 계산하지 않고 memberId만 그대로 Service에 넘긴다 -- files 목록의
        // DRAFT 노출 여부(DEVELOPER 판정 포함)는 ProgramServiceImpl이 MemberRoleQueryPort로 직접
        // 판정한다(Controller가 넘기는 JWT 기반 값이 아니다, ProgramService.getDetail KDoc 참고).
        @Test
        fun `상세 조회는 요청자 ID만으로 Service를 호출하고 첨부파일 목록을 그대로 반환한다`() {
            given(programService.getDetail(anyLong(), anyLong())).willReturn(detailResponseWithFiles())

            mockMvc
                .perform(get("/api/v1/programs/1").with(authOf(2L, "DEVELOPER")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.files[0].fileId").value(5))

            verify(programService).getDetail(1L, 2L)
        }

        @Test
        fun `학생으로 조회해도 요청자 ID만으로 Service를 호출한다`() {
            given(programService.getDetail(anyLong(), anyLong())).willReturn(detailResponseWithFiles())

            mockMvc
                .perform(get("/api/v1/programs/1").with(authOf(1L, "STUDENT")))
                .andExpect(status().isOk)

            verify(programService).getDetail(1L, 1L)
        }
    }
