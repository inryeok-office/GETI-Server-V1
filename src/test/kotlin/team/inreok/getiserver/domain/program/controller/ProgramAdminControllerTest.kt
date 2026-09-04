package team.inreok.getiserver.domain.program.controller

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
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
import team.inreok.getiserver.domain.program.dto.ProgramAdminListResponse
import team.inreok.getiserver.domain.program.dto.ProgramCreateRequest
import team.inreok.getiserver.domain.program.dto.ProgramCreateResponse
import team.inreok.getiserver.domain.program.dto.ProgramStatusUpdateRequest
import team.inreok.getiserver.domain.program.dto.ProgramStatusUpdateResponse
import team.inreok.getiserver.domain.program.dto.ProgramUpdateRequest
import team.inreok.getiserver.domain.program.dto.ProgramUpdateResponse
import team.inreok.getiserver.domain.program.entity.type.ProgramStatus
import team.inreok.getiserver.domain.program.entity.type.ProgramType
import team.inreok.getiserver.domain.program.service.ProgramService
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig
import java.time.LocalDateTime

// SecurityConfig를 명시적으로 Import해 /api/v1/admin/programs가 실제로 TEACHER 또는 DEVELOPER
// 역할을 요구하는지(401/403)까지 검증한다(CompanyAdminControllerTest/ProgramControllerTest와 동일한 방식).
@WebMvcTest(controllers = [ProgramAdminController::class])
@Import(team.inreok.getiserver.global.security.NormalSecurityTestConfig::class)
@EnableWebSecurity
class ProgramAdminControllerTest
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
        private fun anyCreateRequest(): ProgramCreateRequest =
            any(ProgramCreateRequest::class.java)
                ?: ProgramCreateRequest(
                    title = "",
                    programType = ProgramType.SPECIAL_LECTURE,
                    status = ProgramStatus.DRAFT,
                )

        private fun anyUpdateRequest(): ProgramUpdateRequest =
            any(ProgramUpdateRequest::class.java) ?: ProgramUpdateRequest()

        private fun anyStatusUpdateRequest(): ProgramStatusUpdateRequest =
            any(ProgramStatusUpdateRequest::class.java) ?: ProgramStatusUpdateRequest(status = ProgramStatus.DELETED)

        // captor.capture()의 Elvis 더미 전용이다(위 anyCreateRequest()와 달리 any() Matcher를
        // 호출하지 않는다 -- capture()와 함께 쓰면 Matcher가 중복 기록된다).
        private fun emptyCreateRequest(): ProgramCreateRequest =
            ProgramCreateRequest(title = "", programType = ProgramType.SPECIAL_LECTURE, status = ProgramStatus.DRAFT)

        private val createResponse =
            ProgramCreateResponse(
                programId = 1L,
                programType = ProgramType.SPECIAL_LECTURE,
                targetGrades = listOf(2, 3),
                capacity = 20,
                currentApplicants = 0,
                remainingCapacity = 20,
                firstComeServed = true,
                manager = null,
                status = ProgramStatus.DRAFT,
                createdAt = LocalDateTime.now(),
            )

        private val adminListResponse =
            ProgramAdminListResponse(
                content = emptyList(),
                page = 0,
                size = 20,
                totalElements = 0,
                totalPages = 0,
                first = true,
                last = true,
            )

        @Test
        fun `teacher can list admin programs`() {
            given(
                programService.listForAdmin("backend", ProgramStatus.DRAFT, PageRequest.of(1, 2)),
            ).willReturn(adminListResponse)

            mockMvc
                .perform(
                    get(
                        "/api/v1/admin/programs",
                    ).param("query", "backend")
                        .param("status", "DRAFT")
                        .param("page", "1")
                        .param("size", "2")
                        .with(authOf(1L, "TEACHER")),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.totalElements").value(0))
        }

        @Test
        fun `developer can list admin programs`() {
            given(programService.listForAdmin(null, null, PageRequest.of(0, 20))).willReturn(adminListResponse)

            mockMvc.perform(get("/api/v1/admin/programs").with(authOf(2L, "DEVELOPER"))).andExpect(status().isOk)
        }

        @Test
        fun `invalid program status returns bad request`() {
            mockMvc
                .perform(get("/api/v1/admin/programs").param("status", "INVALID").with(authOf(1L, "TEACHER")))
                .andExpect(status().isBadRequest)

            verify(programService, never()).listForAdmin(any(), any(), anyPageable())
        }

        @Test
        fun `student cannot list admin programs`() {
            mockMvc.perform(get("/api/v1/admin/programs").with(authOf(3L, "STUDENT"))).andExpect(status().isForbidden)

            verify(programService, never()).listForAdmin(any(), any(), anyPageable())
        }

        @Test
        fun `unauthenticated request cannot list admin programs`() {
            mockMvc.perform(get("/api/v1/admin/programs")).andExpect(status().isUnauthorized)

            verify(programService, never()).listForAdmin(any(), any(), anyPageable())
        }

        private fun anyPageable(): org.springframework.data.domain.Pageable =
            any(org.springframework.data.domain.Pageable::class.java) ?: PageRequest.of(0, 20)

        private val updateResponse =
            ProgramUpdateResponse(
                programId = 1L,
                capacity = 25,
                currentApplicants = 20,
                remainingCapacity = 5,
                vacancyNotificationCount = 0,
                notificationCreated = false,
                updatedAt = LocalDateTime.now(),
            )

        private val statusUpdateResponse =
            ProgramStatusUpdateResponse(
                programId = 1L,
                status = ProgramStatus.PUBLISHED,
                manager = null,
                notificationCount = 0,
                expiredVacancySubscriptionCount = 0,
                updatedAt = LocalDateTime.now(),
            )

        @Test
        fun `교사는 프로그램을 등록할 수 있고 201을 반환한다`() {
            given(programService.create(anyCreateRequest(), anyLong())).willReturn(createResponse)

            mockMvc
                .perform(
                    post("/api/v1/admin/programs")
                        .with(authOf(1L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "title": "여름방학 특강", "programType": "SPECIAL_LECTURE", "status": "DRAFT" }"""),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.data.programId").value(1))
        }

        @Test
        fun `개발자는 프로그램을 등록할 수 있고 201을 반환한다`() {
            given(programService.create(anyCreateRequest(), anyLong())).willReturn(createResponse)

            mockMvc
                .perform(
                    post("/api/v1/admin/programs")
                        .with(authOf(2L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "title": "여름방학 특강", "programType": "SPECIAL_LECTURE", "status": "DRAFT" }"""),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.data.programId").value(1))
        }

        @Test
        fun `학생은 프로그램을 등록할 수 없고 403을 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/admin/programs")
                        .with(authOf(3L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "title": "여름방학 특강", "programType": "SPECIAL_LECTURE", "status": "DRAFT" }"""),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))

            verify(programService, never()).create(anyCreateRequest(), anyLong())
        }

        @Test
        fun `등록 요청의 fileIds가 Service로 그대로 전달된다`() {
            given(programService.create(anyCreateRequest(), anyLong())).willReturn(createResponse)
            val captor = ArgumentCaptor.forClass(ProgramCreateRequest::class.java)

            mockMvc
                .perform(
                    post("/api/v1/admin/programs")
                        .with(authOf(1L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            { "title": "여름방학 특강", "programType": "SPECIAL_LECTURE",
                              "status": "DRAFT", "fileIds": [1, 2] }
                            """,
                        ),
                ).andExpect(status().isCreated)

            // captor.capture()는 Kotlin에서 null을 반환하는 Mockito의 알려진 특성이라, 실제 값
            // 자체는 쓰이지 않는 더미로 Elvis를 채운다(ProgramServiceImplTest.publishedDiscordEvents와
            // 동일한 이유). 여기서 any()류 Matcher를 다시 호출하면 Matcher가 중복 기록돼
            // InvalidUseOfMatchersException이 된다.
            verify(programService).create(captor.capture() ?: emptyCreateRequest(), anyLong())
            assertThat(captor.value.fileIds).containsExactly(1L, 2L)
        }

        @Test
        fun `인증 없이 프로그램을 등록하면 401 UNAUTHORIZED를 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/admin/programs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "title": "여름방학 특강", "programType": "SPECIAL_LECTURE", "status": "DRAFT" }"""),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        }

        @Test
        fun `교사는 프로그램을 수정할 수 있고 200을 반환한다`() {
            given(programService.update(anyLong(), anyLong(), anyBoolean(), anyUpdateRequest()))
                .willReturn(updateResponse)

            mockMvc
                .perform(
                    patch("/api/v1/admin/programs/1")
                        .with(authOf(1L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "capacity": 25 }"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.capacity").value(25))
        }

        @Test
        fun `개발자는 프로그램을 수정할 수 있고 200을 반환한다`() {
            given(programService.update(anyLong(), anyLong(), anyBoolean(), anyUpdateRequest()))
                .willReturn(updateResponse)

            mockMvc
                .perform(
                    patch("/api/v1/admin/programs/1")
                        .with(authOf(2L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "capacity": 25 }"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.capacity").value(25))
        }

        @Test
        fun `학생은 프로그램을 수정할 수 없고 403을 반환한다`() {
            mockMvc
                .perform(
                    patch("/api/v1/admin/programs/1")
                        .with(authOf(3L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "capacity": 25 }"""),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))

            verify(programService, never()).update(anyLong(), anyLong(), anyBoolean(), anyUpdateRequest())
        }

        @Test
        fun `수정 요청의 fileIds가 Service로 그대로 전달된다`() {
            given(programService.update(anyLong(), anyLong(), anyBoolean(), anyUpdateRequest()))
                .willReturn(updateResponse)
            val captor = ArgumentCaptor.forClass(ProgramUpdateRequest::class.java)

            mockMvc
                .perform(
                    patch("/api/v1/admin/programs/1")
                        .with(authOf(1L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "fileIds": [] }"""),
                ).andExpect(status().isOk)

            verify(
                programService,
            ).update(anyLong(), anyLong(), anyBoolean(), captor.capture() ?: ProgramUpdateRequest())
            assertThat(captor.value.fileIds).isEqualTo(emptyList<Long>())
        }

        @Test
        fun `인증 없이 프로그램을 수정하면 401 UNAUTHORIZED를 반환한다`() {
            mockMvc
                .perform(
                    patch("/api/v1/admin/programs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "capacity": 25 }"""),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        }

        @Test
        fun `교사는 프로그램 상태를 변경할 수 있고 200을 반환한다`() {
            given(programService.changeStatus(anyLong(), anyLong(), anyBoolean(), anyStatusUpdateRequest()))
                .willReturn(statusUpdateResponse)

            mockMvc
                .perform(
                    patch("/api/v1/admin/programs/1/status")
                        .with(authOf(1L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "status": "PUBLISHED" }"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
        }

        @Test
        fun `개발자는 프로그램 상태를 변경할 수 있고 200을 반환한다`() {
            given(programService.changeStatus(anyLong(), anyLong(), anyBoolean(), anyStatusUpdateRequest()))
                .willReturn(statusUpdateResponse)

            mockMvc
                .perform(
                    patch("/api/v1/admin/programs/1/status")
                        .with(authOf(2L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "status": "PUBLISHED" }"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
        }

        @Test
        fun `학생은 프로그램 상태를 변경할 수 없고 403을 반환한다`() {
            mockMvc
                .perform(
                    patch("/api/v1/admin/programs/1/status")
                        .with(authOf(3L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "status": "PUBLISHED" }"""),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))

            verify(programService, never())
                .changeStatus(anyLong(), anyLong(), anyBoolean(), anyStatusUpdateRequest())
        }

        @Test
        fun `인증 없이 프로그램 상태를 변경하면 401 UNAUTHORIZED를 반환한다`() {
            mockMvc
                .perform(
                    patch("/api/v1/admin/programs/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "status": "PUBLISHED" }"""),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        }
    }
