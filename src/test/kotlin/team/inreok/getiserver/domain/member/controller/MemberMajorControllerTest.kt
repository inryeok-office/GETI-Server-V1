package team.inreok.getiserver.domain.member.controller

import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.member.dto.MemberMajorItemResponse
import team.inreok.getiserver.domain.member.dto.MemberMajorsResponse
import team.inreok.getiserver.domain.member.exception.DuplicateMajorException
import team.inreok.getiserver.domain.member.exception.MajorNotFoundException
import team.inreok.getiserver.domain.member.service.MemberMajorService
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig

// SecurityConfig를 명시적으로 Import해 /api/v1/me/**가 실제로 인증을 요구하는지(401)까지 검증한다.
@WebMvcTest(controllers = [MemberMajorController::class])
@Import(team.inreok.getiserver.global.security.NormalSecurityTestConfig::class)
@EnableWebSecurity
class MemberMajorControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var memberMajorService: MemberMajorService

        @MockitoBean
        private lateinit var jwtTokenProvider: JwtTokenProvider

        private fun authOf(
            memberId: Long,
            role: String = "STUDENT",
        ) = authentication(
            UsernamePasswordAuthenticationToken(memberId, null, listOf(SimpleGrantedAuthority("ROLE_$role"))),
        )

        @Test
        fun `내 전공을 교체하면 200과 함께 변경된 목록을 반환한다`() {
            given(memberMajorService.replaceAll(1L, listOf(10L, 20L))).willReturn(
                MemberMajorsResponse(
                    listOf(
                        MemberMajorItemResponse(10L, "소프트웨어"),
                        MemberMajorItemResponse(20L, "인공지능"),
                    ),
                ),
            )

            mockMvc
                .perform(
                    patch("/api/v1/me/majors")
                        .with(authOf(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"majorIds":[10,20]}"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.majors.length()").value(2))
                .andExpect(jsonPath("$.data.majors[0].majorId").value(10))
        }

        @Test
        fun `Query Parameter로 다른 memberId를 보내도 인증된 본인 memberId로만 처리한다`() {
            given(memberMajorService.replaceAll(1L, listOf(10L))).willReturn(
                MemberMajorsResponse(listOf(MemberMajorItemResponse(10L, "소프트웨어"))),
            )

            mockMvc
                .perform(
                    patch("/api/v1/me/majors")
                        .with(authOf(1L))
                        .param("memberId", "999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"majorIds":[10]}"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.majors[0].majorId").value(10))
        }

        @Test
        fun `존재하지 않는 전공이면 404 MAJOR_NOT_FOUND를 반환한다`() {
            given(memberMajorService.replaceAll(1L, listOf(999L))).willThrow(MajorNotFoundException(listOf(999L)))

            mockMvc
                .perform(
                    patch("/api/v1/me/majors")
                        .with(authOf(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"majorIds":[999]}"""),
                ).andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("MAJOR_NOT_FOUND"))
        }

        @Test
        fun `중복된 majorId가 있으면 409 DUPLICATE_MAJOR를 반환한다`() {
            given(memberMajorService.replaceAll(1L, listOf(10L, 10L))).willThrow(DuplicateMajorException())

            mockMvc
                .perform(
                    patch("/api/v1/me/majors")
                        .with(authOf(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"majorIds":[10,10]}"""),
                ).andExpect(status().isConflict)
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_MAJOR"))
        }

        @Test
        fun `요청자가 STUDENT가 아니면 403 NOT_A_STUDENT를 반환한다`() {
            mockMvc
                .perform(
                    patch("/api/v1/me/majors")
                        .with(authOf(1L, role = "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"majorIds":[10]}"""),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("NOT_A_STUDENT"))
        }

        @Test
        fun `인증되지 않은 요청은 401 UNAUTHORIZED를 반환한다`() {
            mockMvc
                .perform(
                    patch("/api/v1/me/majors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"majorIds":[]}"""),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        }
    }
