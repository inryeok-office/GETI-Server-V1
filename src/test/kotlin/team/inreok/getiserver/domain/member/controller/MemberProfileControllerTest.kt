package team.inreok.getiserver.domain.member.controller

import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.member.dto.MemberProfileUpdateResponse
import team.inreok.getiserver.domain.member.dto.MyProfileResponse
import team.inreok.getiserver.domain.member.entity.type.AcademicStatus
import team.inreok.getiserver.domain.member.entity.type.DepartmentType
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.exception.MemberNotFoundException
import team.inreok.getiserver.domain.member.exception.MemberProfileNotFoundException
import team.inreok.getiserver.domain.member.exception.MemberProfileValidationException
import team.inreok.getiserver.domain.member.service.MemberService
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime

@WebMvcTest(controllers = [MemberProfileController::class])
class MemberProfileControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var memberService: MemberService

        private val objectMapper = JsonMapper()

        @Test
        fun `내 프로필을 조회하면 200과 함께 프로필을 반환한다`() {
            given(memberService.getMyProfile(1L)).willReturn(
                MyProfileResponse(
                    memberId = 1L,
                    name = "홍길동",
                    email = "student@example.com",
                    roles = listOf(RoleType.STUDENT),
                    status = MemberStatus.ACTIVE,
                    academicStatus = AcademicStatus.ENROLLED,
                    cohort = 3,
                    department = DepartmentType.SW_DEVELOPMENT,
                    phone = "010-0000-0000",
                    profileImageUrl = null,
                    desiredJob = "Backend Developer",
                    bio = "안녕하세요",
                    githubUrl = "https://github.com/example",
                    isPublic = true,
                    majors = listOf("소프트웨어"),
                    techStacks = listOf("Kotlin"),
                ),
            )

            mockMvc
                .perform(
                    get("/api/v1/me/profile")
                        .param("memberId", "1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-token"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.memberId").value(1))
                .andExpect(jsonPath("$.data.email").value("student@example.com"))
                .andExpect(jsonPath("$.data.roles[0]").value("STUDENT"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.majors[0]").value("소프트웨어"))
        }

        @Test
        fun `내 프로필이 없으면 404 PROFILE_NOT_FOUND를 반환한다`() {
            given(memberService.getMyProfile(999L)).willThrow(MemberProfileNotFoundException(999L))

            mockMvc
                .perform(
                    get("/api/v1/me/profile")
                        .param("memberId", "999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-token"),
                ).andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("PROFILE_NOT_FOUND"))
        }

        @Test
        fun `내 프로필을 수정하면 200과 함께 변경된 프로필을 반환한다`() {
            val requestBody = """{"bio":"안녕하세요"}"""
            given(memberService.updateProfile(1L, objectMapper.readTree(requestBody))).willReturn(
                MemberProfileUpdateResponse(
                    memberId = 1L,
                    name = "홍길동",
                    department = DepartmentType.SW_DEVELOPMENT,
                    phone = "010-0000-0000",
                    desiredJob = "Backend Developer",
                    bio = "안녕하세요",
                    githubUrl = "https://github.com/example",
                    isPublic = true,
                    profileImageUrl = null,
                    updatedAt = LocalDateTime.of(2026, 7, 31, 0, 0),
                ),
            )

            mockMvc
                .perform(
                    patch("/api/v1/me/profile")
                        .param("memberId", "1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.memberId").value(1))
                .andExpect(jsonPath("$.data.bio").value("안녕하세요"))
                .andExpect(jsonPath("$.data.isPublic").value(true))
        }

        @Test
        fun `존재하지 않는 회원이면 404 MEMBER_NOT_FOUND를 반환한다`() {
            val requestBody = """{"bio":"x"}"""
            given(memberService.updateProfile(999L, objectMapper.readTree(requestBody)))
                .willThrow(MemberNotFoundException(999L))

            mockMvc
                .perform(
                    patch("/api/v1/me/profile")
                        .param("memberId", "999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody),
                ).andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("MEMBER_NOT_FOUND"))
        }

        @Test
        fun `요청 값이 올바르지 않으면 400 PROFILE_VALIDATION_FAILED를 반환한다`() {
            val requestBody = """{"department":"INVALID"}"""
            given(memberService.updateProfile(1L, objectMapper.readTree(requestBody)))
                .willThrow(MemberProfileValidationException("department 값이 올바르지 않습니다."))

            mockMvc
                .perform(
                    patch("/api/v1/me/profile")
                        .param("memberId", "1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("PROFILE_VALIDATION_FAILED"))
        }

        @Test
        fun `Authorization Header가 없으면 400을 반환한다`() {
            mockMvc
                .perform(
                    patch("/api/v1/me/profile")
                        .param("memberId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"bio":"x"}"""),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `memberId Query Parameter가 없으면 400을 반환한다`() {
            mockMvc
                .perform(
                    patch("/api/v1/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"bio":"x"}"""),
                ).andExpect(status().isBadRequest)
        }
    }
