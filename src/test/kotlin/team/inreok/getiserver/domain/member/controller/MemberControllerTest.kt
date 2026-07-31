package team.inreok.getiserver.domain.member.controller

import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpHeaders
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.member.dto.MemberProfileResponse
import team.inreok.getiserver.domain.member.dto.MemberSearchItemResponse
import team.inreok.getiserver.domain.member.dto.MemberSearchResponse
import team.inreok.getiserver.domain.member.entity.type.DepartmentType
import team.inreok.getiserver.domain.member.exception.MemberNotFoundException
import team.inreok.getiserver.domain.member.exception.NameRequiredException
import team.inreok.getiserver.domain.member.service.MemberSearchService
import team.inreok.getiserver.domain.member.service.MemberService

@WebMvcTest(controllers = [MemberController::class])
class MemberControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var memberService: MemberService

        @MockitoBean
        private lateinit var memberSearchService: MemberSearchService

        @Test
        fun `학생 프로필을 조회하면 200과 함께 프로필을 반환한다`() {
            given(memberService.getProfile(1L)).willReturn(
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
                    isPublic = true,
                    profileRestricted = false,
                ),
            )

            mockMvc
                .perform(get("/api/v1/members/1").header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.memberId").value(1))
                .andExpect(jsonPath("$.data.name").value("홍길동"))
                .andExpect(jsonPath("$.data.cohort").value(3))
                .andExpect(jsonPath("$.data.department").value("SW_DEVELOPMENT"))
                .andExpect(jsonPath("$.data.majors[0]").value("소프트웨어"))
                .andExpect(jsonPath("$.data.techStacks.length()").value(2))
                .andExpect(jsonPath("$.data.desiredJob").value("Backend Developer"))
                .andExpect(jsonPath("$.data.isPublic").value(true))
                .andExpect(jsonPath("$.data.profileRestricted").value(false))
        }

        @Test
        fun `존재하지 않는 회원이면 404 MEMBER_NOT_FOUND를 반환한다`() {
            given(memberService.getProfile(999L)).willThrow(MemberNotFoundException(999L))

            mockMvc
                .perform(get("/api/v1/members/999").header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("MEMBER_NOT_FOUND"))
        }

        @Test
        fun `Authorization Header가 없으면 400을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/members/1"))
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `Authorization Header가 빈 값이면 400을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/members/1").header(HttpHeaders.AUTHORIZATION, ""))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
        }

        @Test
        fun `이름으로 검색하면 200과 함께 목록을 반환한다`() {
            given(
                memberSearchService.search("홍길동", null, null, null, null, null, PageRequest.of(0, 20)),
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
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
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
            given(memberSearchService.search(null, null, null, null, null, null, PageRequest.of(0, 20)))
                .willThrow(NameRequiredException())

            mockMvc
                .perform(get("/api/v1/members").header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("NAME_REQUIRED"))
        }
    }
