package team.inreok.getiserver.domain.member.controller

import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.member.dto.MemberProfileLinkResponse
import team.inreok.getiserver.domain.member.dto.MemberProfileUpdateResponse
import team.inreok.getiserver.domain.member.dto.MyProfileResponse
import team.inreok.getiserver.domain.member.entity.type.AcademicStatus
import team.inreok.getiserver.domain.member.entity.type.DepartmentType
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.exception.MemberProfileNotFoundException
import team.inreok.getiserver.domain.member.exception.MemberProfileValidationException
import team.inreok.getiserver.domain.member.service.MemberService
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime

// SecurityConfig를 명시적으로 Import해 /api/v1/me/**가 실제로 인증을 요구하는지(401)까지
// 검증한다(WebPageableConfig와 동일하게 일반 @Configuration이라 @WebMvcTest가 자동 인식하지 않음).
@WebMvcTest(controllers = [MemberProfileController::class])
@Import(team.inreok.getiserver.global.security.NormalSecurityTestConfig::class)
@EnableWebSecurity
class MemberProfileControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var memberService: MemberService

        // SecurityConfig가 Bean으로 필요로 하지만, 이 Test는 JwtAuthenticationFilter의 실제 파싱 결과를
        // 쓰지 않고 SecurityMockMvcRequestPostProcessors.authentication(...)으로 SecurityContext를
        // 직접 채우므로 동작(Stubbing) 없이 존재만 하면 된다.
        @MockitoBean
        private lateinit var jwtTokenProvider: JwtTokenProvider

        private val objectMapper = JsonMapper()

        private fun authOf(memberId: Long) =
            authentication(UsernamePasswordAuthenticationToken(memberId, null, emptyList()))

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
                    links = listOf(MemberProfileLinkResponse(label = "기술 블로그", url = "https://blog.example.com")),
                    techStacks = listOf("Kotlin"),
                ),
            )

            mockMvc
                .perform(get("/api/v1/me/profile").with(authOf(1L)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.memberId").value(1))
                .andExpect(jsonPath("$.data.email").value("student@example.com"))
                .andExpect(jsonPath("$.data.roles[0]").value("STUDENT"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.majors[0]").value("소프트웨어"))
                .andExpect(jsonPath("$.data.links[0].label").value("기술 블로그"))
                .andExpect(jsonPath("$.data.links[0].url").value("https://blog.example.com"))
        }

        @Test
        fun `Query Parameter로 다른 memberId를 보내도 인증된 본인 memberId로만 처리한다`() {
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
                    links = listOf(MemberProfileLinkResponse(label = "기술 블로그", url = "https://blog.example.com")),
                    techStacks = listOf("Kotlin"),
                ),
            )

            mockMvc
                .perform(get("/api/v1/me/profile").with(authOf(1L)).param("memberId", "999"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.memberId").value(1))
        }

        @Test
        fun `내 프로필이 없으면 404 PROFILE_NOT_FOUND를 반환한다`() {
            given(memberService.getMyProfile(999L)).willThrow(MemberProfileNotFoundException(999L))

            mockMvc
                .perform(get("/api/v1/me/profile").with(authOf(999L)))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("PROFILE_NOT_FOUND"))
        }

        @Test
        fun `내 프로필을 수정하면 200과 함께 변경된 프로필을 반환한다`() {
            val requestBody = """{"cohort":10,"bio":"안녕하세요"}"""
            given(memberService.updateProfile(1L, objectMapper.readTree(requestBody))).willReturn(
                MemberProfileUpdateResponse(
                    memberId = 1L,
                    name = "홍길동",
                    cohort = 10,
                    department = DepartmentType.SW_DEVELOPMENT,
                    phone = "010-0000-0000",
                    desiredJob = "Backend Developer",
                    bio = "안녕하세요",
                    githubUrl = "https://github.com/example",
                    links = listOf(MemberProfileLinkResponse(label = "기술 블로그", url = "https://blog.example.com")),
                    isPublic = true,
                    profileImageUrl = null,
                    updatedAt = LocalDateTime.of(2026, 7, 31, 0, 0),
                ),
            )

            mockMvc
                .perform(
                    patch("/api/v1/me/profile")
                        .with(authOf(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.memberId").value(1))
                .andExpect(jsonPath("$.data.cohort").value(10))
                .andExpect(jsonPath("$.data.bio").value("안녕하세요"))
                .andExpect(jsonPath("$.data.links[0].label").value("기술 블로그"))
                .andExpect(jsonPath("$.data.isPublic").value(true))
        }

        @Test
        fun `links를 포함해 수정하면 200과 함께 변경된 링크 목록을 반환한다`() {
            val requestBody = """{"links":[{"label":"블로그","url":"https://blog.example.com/me"}]}"""
            given(memberService.updateProfile(1L, objectMapper.readTree(requestBody))).willReturn(
                MemberProfileUpdateResponse(
                    memberId = 1L,
                    name = "홍길동",
                    cohort = 10,
                    department = DepartmentType.SW_DEVELOPMENT,
                    phone = "010-0000-0000",
                    desiredJob = "Backend Developer",
                    bio = "안녕하세요",
                    githubUrl = "https://github.com/example",
                    links = listOf(MemberProfileLinkResponse(label = "블로그", url = "https://blog.example.com/me")),
                    isPublic = true,
                    profileImageUrl = null,
                    updatedAt = LocalDateTime.of(2026, 7, 31, 0, 0),
                ),
            )

            mockMvc
                .perform(
                    patch("/api/v1/me/profile")
                        .with(authOf(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.links[0].label").value("블로그"))
                .andExpect(jsonPath("$.data.links[0].url").value("https://blog.example.com/me"))
        }

        @Test
        fun `links의 url이 http-https가 아니면 400 PROFILE_VALIDATION_FAILED를 반환한다`() {
            val requestBody = """{"links":[{"label":"위험한 링크","url":"javascript:alert(1)"}]}"""
            given(memberService.updateProfile(1L, objectMapper.readTree(requestBody)))
                .willThrow(MemberProfileValidationException("links의 url은 http 또는 https만 허용합니다."))

            mockMvc
                .perform(
                    patch("/api/v1/me/profile")
                        .with(authOf(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("PROFILE_VALIDATION_FAILED"))
        }

        @Test
        fun `존재하지 않는 회원이면 404 PROFILE_NOT_FOUND를 반환한다`() {
            val requestBody = """{"bio":"x"}"""
            given(memberService.updateProfile(999L, objectMapper.readTree(requestBody)))
                .willThrow(MemberProfileNotFoundException(999L))

            mockMvc
                .perform(
                    patch("/api/v1/me/profile")
                        .with(authOf(999L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody),
                ).andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("PROFILE_NOT_FOUND"))
        }

        @Test
        fun `요청 값이 올바르지 않으면 400 PROFILE_VALIDATION_FAILED를 반환한다`() {
            val requestBody = """{"department":"INVALID"}"""
            given(memberService.updateProfile(1L, objectMapper.readTree(requestBody)))
                .willThrow(MemberProfileValidationException("department 값이 올바르지 않습니다."))

            mockMvc
                .perform(
                    patch("/api/v1/me/profile")
                        .with(authOf(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("PROFILE_VALIDATION_FAILED"))
        }

        @Test
        fun `인증되지 않은 요청은 401 UNAUTHORIZED를 반환한다`() {
            mockMvc
                .perform(get("/api/v1/me/profile"))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        }

        @Test
        fun `인증되지 않은 수정 요청도 401 UNAUTHORIZED를 반환한다`() {
            mockMvc
                .perform(
                    patch("/api/v1/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"bio":"x"}"""),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        }
    }
