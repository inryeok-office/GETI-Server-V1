package team.inreok.getiserver.domain.recommendation.controller

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyBoolean
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.recommendation.dto.RecommendationItemResponse
import team.inreok.getiserver.domain.recommendation.dto.RecommendationJobResponse
import team.inreok.getiserver.domain.recommendation.dto.RecommendationListResponse
import team.inreok.getiserver.domain.recommendation.dto.RecommendationSettingResponse
import team.inreok.getiserver.domain.recommendation.dto.RecommendationStatus
import team.inreok.getiserver.domain.recommendation.entity.type.SuitabilityLevel
import team.inreok.getiserver.domain.recommendation.exception.RecommendationJobNotFoundException
import team.inreok.getiserver.domain.recommendation.service.RecommendationService
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig
import java.time.LocalDateTime

// SecurityConfig를 명시적으로 Import해 /api/v1/recommendations 이하가 STUDENT Role을 실제로
// 요구하는지(403)까지 검증한다(NotificationControllerTest와 동일한 방식).
@WebMvcTest(controllers = [RecommendationController::class])
@Import(SecurityConfig::class)
@EnableWebSecurity
class RecommendationControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var recommendationService: RecommendationService

        @MockitoBean
        private lateinit var jwtTokenProvider: JwtTokenProvider

        private val memberId = 1L

        private fun studentAuth() =
            authentication(
                UsernamePasswordAuthenticationToken(memberId, null, listOf(SimpleGrantedAuthority("ROLE_STUDENT"))),
            )

        private fun teacherAuth() =
            authentication(
                UsernamePasswordAuthenticationToken(100L, null, listOf(SimpleGrantedAuthority("ROLE_TEACHER"))),
            )

        private fun recommendationItemOf() =
            RecommendationItemResponse(
                job =
                    RecommendationJobResponse(
                        jobId = 1L,
                        title = "백엔드 개발 인턴",
                        companyName = "인력개발원",
                        companyLogoUrl = null,
                        recruitmentEndedAt = null,
                    ),
                score = 82,
                suitability = SuitabilityLevel.RECOMMENDED,
                rank = 1,
                reasons = emptyList(),
            )

        // ---------- 조회 ----------

        @Test
        fun `학생은 자신의 추천 목록을 조회할 수 있다`() {
            val response =
                RecommendationListResponse(
                    enabled = true,
                    status = RecommendationStatus.READY,
                    generatedAt = LocalDateTime.of(2026, 8, 17, 6, 0),
                    items = listOf(recommendationItemOf()),
                )
            given(recommendationService.getMyRecommendations(memberId)).willReturn(response)

            mockMvc
                .perform(get("/api/v1/recommendations").with(studentAuth()))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.items[0].job.jobId").value(1))
                .andExpect(jsonPath("$.data.items[0].score").value(82))
                .andExpect(jsonPath("$.data.items[0].suitability").value("RECOMMENDED"))
        }

        @Test
        fun `추천이 꺼져 있으면 DISABLED와 빈 목록을 반환한다`() {
            given(recommendationService.getMyRecommendations(memberId))
                .willReturn(
                    RecommendationListResponse(
                        enabled = false,
                        status = RecommendationStatus.DISABLED,
                        generatedAt = null,
                        items = emptyList(),
                    ),
                )

            mockMvc
                .perform(get("/api/v1/recommendations").with(studentAuth()))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.status").value("DISABLED"))
                .andExpect(jsonPath("$.data.items").isEmpty())
        }

        @Test
        fun `인증 없이 조회하면 401이다`() {
            mockMvc
                .perform(get("/api/v1/recommendations"))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))

            verify(recommendationService, never()).getMyRecommendations(anyLong())
        }

        @Test
        fun `학생이 아니면 추천을 조회할 수 없다`() {
            mockMvc
                .perform(get("/api/v1/recommendations").with(teacherAuth()))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))

            verify(recommendationService, never()).getMyRecommendations(anyLong())
        }

        @Test
        fun `요청한 사용자 본인의 ID로만 조회한다`() {
            given(recommendationService.getMyRecommendations(anyLong()))
                .willReturn(
                    RecommendationListResponse(
                        enabled = false,
                        status = RecommendationStatus.DISABLED,
                        generatedAt = null,
                        items = emptyList(),
                    ),
                )

            mockMvc.perform(get("/api/v1/recommendations").with(studentAuth())).andExpect(status().isOk)

            val captor = ArgumentCaptor.forClass(Long::class.javaObjectType)
            verify(recommendationService).getMyRecommendations(captor.capture() ?: 0L)
            assertThat(captor.value).isEqualTo(memberId)
        }

        // ---------- ON/OFF ----------

        @Test
        fun `추천 기능을 켤 수 있다`() {
            given(recommendationService.updateSetting(memberId, true))
                .willReturn(
                    RecommendationSettingResponse(enabled = true, updatedAt = LocalDateTime.of(2026, 8, 17, 6, 0)),
                )

            mockMvc
                .perform(
                    patch("/api/v1/recommendations/settings")
                        .with(studentAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "enabled": true }"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.enabled").value(true))
        }

        @Test
        fun `필수 값이 없으면 400이다`() {
            mockMvc
                .perform(
                    patch("/api/v1/recommendations/settings")
                        .with(studentAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ }"""),
                ).andExpect(status().isBadRequest)

            verify(recommendationService, never()).updateSetting(anyLong(), anyBoolean())
        }

        @Test
        fun `인증 없이 설정을 변경하면 401이다`() {
            mockMvc
                .perform(
                    patch("/api/v1/recommendations/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "enabled": true }"""),
                ).andExpect(status().isUnauthorized)

            verify(recommendationService, never()).updateSetting(anyLong(), anyBoolean())
        }

        @Test
        fun `학생이 아니면 설정을 변경할 수 없다`() {
            mockMvc
                .perform(
                    patch("/api/v1/recommendations/settings")
                        .with(teacherAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "enabled": true }"""),
                ).andExpect(status().isForbidden)
        }

        // ---------- 관심 없음 ----------

        @Test
        fun `공고를 관심 없음으로 설정하면 204다`() {
            mockMvc
                .perform(post("/api/v1/recommendations/1/not-interested").with(studentAuth()))
                .andExpect(status().isNoContent)

            verify(recommendationService).markNotInterested(memberId, 1L)
        }

        @Test
        fun `관심 없음 대상 공고가 없으면 404다`() {
            willThrow(RecommendationJobNotFoundException(999L))
                .given(recommendationService)
                .markNotInterested(memberId, 999L)

            mockMvc
                .perform(post("/api/v1/recommendations/999/not-interested").with(studentAuth()))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("JOB_NOT_FOUND"))
        }

        @Test
        fun `관심 없음을 두 번 요청해도 204다`() {
            mockMvc
                .perform(post("/api/v1/recommendations/1/not-interested").with(studentAuth()))
                .andExpect(status().isNoContent)
            mockMvc
                .perform(post("/api/v1/recommendations/1/not-interested").with(studentAuth()))
                .andExpect(status().isNoContent)
        }

        @Test
        fun `인증 없이 관심 없음을 설정하면 401이다`() {
            mockMvc
                .perform(post("/api/v1/recommendations/1/not-interested"))
                .andExpect(status().isUnauthorized)

            verify(recommendationService, never()).markNotInterested(anyLong(), anyLong())
        }

        // ---------- 관심 없음 해제 ----------

        @Test
        fun `관심 없음을 해제하면 204다`() {
            mockMvc
                .perform(delete("/api/v1/recommendations/1/not-interested").with(studentAuth()))
                .andExpect(status().isNoContent)

            verify(recommendationService).removeNotInterested(memberId, 1L)
        }

        @Test
        fun `이미 해제된 상태에서 다시 해제해도 204다`() {
            mockMvc
                .perform(delete("/api/v1/recommendations/1/not-interested").with(studentAuth()))
                .andExpect(status().isNoContent)
        }

        @Test
        fun `인증 없이 관심 없음을 해제하면 401이다`() {
            mockMvc
                .perform(delete("/api/v1/recommendations/1/not-interested"))
                .andExpect(status().isUnauthorized)

            verify(recommendationService, never()).removeNotInterested(anyLong(), anyLong())
        }
    }
