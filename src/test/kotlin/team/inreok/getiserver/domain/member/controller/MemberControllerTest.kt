package team.inreok.getiserver.domain.member.controller

import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.member.dto.MemberProfileLinkResponse
import team.inreok.getiserver.domain.member.dto.MemberProfileResponse
import team.inreok.getiserver.domain.member.dto.MemberSearchItemResponse
import team.inreok.getiserver.domain.member.dto.MemberSearchResponse
import team.inreok.getiserver.domain.member.entity.type.DepartmentType
import team.inreok.getiserver.domain.member.exception.MemberNotFoundException
import team.inreok.getiserver.domain.member.exception.NameRequiredException
import team.inreok.getiserver.domain.member.service.MemberSearchService
import team.inreok.getiserver.domain.member.service.MemberService
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig

// SecurityConfig를 명시적으로 Import해 /api/v1/members가 실제로 인증을 요구하는지(401)까지 검증한다.
@WebMvcTest(controllers = [MemberController::class])
@Import(team.inreok.getiserver.global.security.NormalSecurityTestConfig::class)
@EnableWebSecurity
class MemberControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var memberService: MemberService

        @MockitoBean
        private lateinit var memberSearchService: MemberSearchService

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

        /**
         * 교사·개발자가 비공개 학생을 조회했을 때 Service가 만드는 응답이다. 마스킹 판정 자체는
         * `MemberServiceTest`가 검증하고, 여기서는 Controller가 그 응답을 그대로 통과시키는지만 본다.
         */
        private fun privateProfileSeenByPrivileged() =
            MemberProfileResponse(
                memberId = 1L,
                name = "홍길동",
                profileImageUrl = null,
                cohort = 3,
                department = DepartmentType.SW_DEVELOPMENT,
                majors = listOf("소프트웨어"),
                techStacks = listOf("Kotlin"),
                desiredJob = "Backend Developer",
                bio = "비공개 자기소개",
                links = listOf(MemberProfileLinkResponse(label = "기술 블로그", url = "https://blog.example.com")),
                isPublic = false,
                profileRestricted = false,
            )

        @Test
        fun `학생 프로필을 조회하면 200과 함께 프로필을 반환한다`() {
            given(memberService.getProfile(1L, 1L)).willReturn(
                MemberProfileResponse(
                    memberId = 1L,
                    name = "홍길동",
                    profileImageUrl = null,
                    cohort = 3,
                    department = DepartmentType.SW_DEVELOPMENT,
                    majors = listOf("소프트웨어"),
                    techStacks = listOf("Kotlin", "Spring Boot"),
                    desiredJob = "Backend Developer",
                    bio = "안녕하세요",
                    links = listOf(MemberProfileLinkResponse(label = "기술 블로그", url = "https://blog.example.com")),
                    isPublic = true,
                    profileRestricted = false,
                ),
            )

            mockMvc
                .perform(get("/api/v1/members/1").with(authOf(1L, "STUDENT")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.memberId").value(1))
                .andExpect(jsonPath("$.data.name").value("홍길동"))
                .andExpect(jsonPath("$.data.cohort").value(3))
                .andExpect(jsonPath("$.data.department").value("SW_DEVELOPMENT"))
                .andExpect(jsonPath("$.data.majors[0]").value("소프트웨어"))
                .andExpect(jsonPath("$.data.techStacks.length()").value(2))
                .andExpect(jsonPath("$.data.desiredJob").value("Backend Developer"))
                .andExpect(jsonPath("$.data.links[0].label").value("기술 블로그"))
                .andExpect(jsonPath("$.data.links[0].url").value("https://blog.example.com"))
                .andExpect(jsonPath("$.data.isPublic").value(true))
                .andExpect(jsonPath("$.data.profileRestricted").value(false))
        }

        @Test
        fun `존재하지 않는 회원이면 404 MEMBER_NOT_FOUND를 반환한다`() {
            given(memberService.getProfile(999L, 1L)).willThrow(MemberNotFoundException(999L))

            mockMvc
                .perform(get("/api/v1/members/999").with(authOf(1L, "STUDENT")))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("MEMBER_NOT_FOUND"))
        }

        @Test
        fun `교사도 학생 프로필을 조회할 수 있다`() {
            given(memberService.getProfile(1L, 2L)).willReturn(privateProfileSeenByPrivileged())

            mockMvc
                .perform(get("/api/v1/members/1").with(authOf(2L, "TEACHER")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.memberId").value(1))
                // 비공개 프로필이지만 교사에게는 가려지지 않는다(Issue #114).
                .andExpect(jsonPath("$.data.bio").value("비공개 자기소개"))
                .andExpect(jsonPath("$.data.isPublic").value(false))
                .andExpect(jsonPath("$.data.profileRestricted").value(false))
        }

        @Test
        fun `개발자도 학생 프로필을 조회할 수 있다`() {
            given(memberService.getProfile(1L, 3L)).willReturn(privateProfileSeenByPrivileged())

            mockMvc
                .perform(get("/api/v1/members/1").with(authOf(3L, "DEVELOPER")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.profileRestricted").value(false))
        }

        @Test
        fun `Role이 하나도 없으면 403 PROFILE_VIEW_FORBIDDEN을 반환한다`() {
            // 승인 대기(PENDING) 교직원은 Role을 부여받지 못한 채 로그인할 수 있고
            // (OAuthMemberPortImpl.autoGrantRoleFor), SecurityConfig는 이 경로에 인증만 요구하므로
            // Controller까지 도달한다. 여기서 막지 않으면 승인 절차를 우회한다.
            mockMvc
                .perform(get("/api/v1/members/1").with(authOf(4L)))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("PROFILE_VIEW_FORBIDDEN"))
        }

        @Test
        fun `인증되지 않은 요청은 401 UNAUTHORIZED를 반환한다`() {
            mockMvc
                .perform(get("/api/v1/members/1"))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        }

        @Test
        fun `이름으로 검색하면 200과 함께 목록을 반환한다`() {
            given(
                memberSearchService.search(1L, "홍길동", null, null, null, null, null, PageRequest.of(0, 20)),
            ).willReturn(
                MemberSearchResponse(
                    content =
                        listOf(
                            MemberSearchItemResponse(
                                memberId = 1L,
                                name = "홍길동",
                                profileImageUrl = null,
                                cohort = 3,
                                department = DepartmentType.SW_DEVELOPMENT,
                                isPublic = true,
                            ),
                        ),
                    page = 0,
                    size = 20,
                    totalElements = 1,
                    totalPages = 1,
                    first = true,
                    last = true,
                ),
            )

            mockMvc
                .perform(
                    get("/api/v1/members")
                        .with(authOf(1L, "STUDENT"))
                        .param("name", "홍길동"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].memberId").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.first").value(true))
                .andExpect(jsonPath("$.data.last").value(true))
        }

        @Test
        fun `name이 없으면 400 NAME_REQUIRED를 반환한다`() {
            given(memberSearchService.search(1L, null, null, null, null, null, null, PageRequest.of(0, 20)))
                .willThrow(NameRequiredException())

            mockMvc
                .perform(get("/api/v1/members").with(authOf(1L, "STUDENT")))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("NAME_REQUIRED"))
        }

        @Test
        fun `검색도 인증되지 않으면 401 UNAUTHORIZED를 반환한다`() {
            mockMvc
                .perform(get("/api/v1/members").param("name", "홍길동"))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        }
    }
