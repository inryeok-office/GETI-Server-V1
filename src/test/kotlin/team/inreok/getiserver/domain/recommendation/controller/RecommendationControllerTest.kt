package team.inreok.getiserver.domain.recommendation.controller

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
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
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.entity.type.PostingType
import team.inreok.getiserver.domain.recommendation.dto.RecommendationExclusionListResponse
import team.inreok.getiserver.domain.recommendation.dto.RecommendationExclusionResponse
import team.inreok.getiserver.domain.recommendation.dto.RecommendationItemResponse
import team.inreok.getiserver.domain.recommendation.dto.RecommendationJobResponse
import team.inreok.getiserver.domain.recommendation.dto.RecommendationListResponse
import team.inreok.getiserver.domain.recommendation.dto.RecommendationSettingResponse
import team.inreok.getiserver.domain.recommendation.dto.RecommendationStatus
import team.inreok.getiserver.domain.recommendation.entity.type.ExclusionType
import team.inreok.getiserver.domain.recommendation.entity.type.SuitabilityLevel
import team.inreok.getiserver.domain.recommendation.exception.RecommendationExclusionAlreadyExistsException
import team.inreok.getiserver.domain.recommendation.exception.RecommendationExclusionNotFoundException
import team.inreok.getiserver.domain.recommendation.exception.RecommendationJobNotFoundException
import team.inreok.getiserver.domain.recommendation.exception.RecommendationNotEnrolledException
import team.inreok.getiserver.domain.recommendation.service.RecommendationService
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig
import java.time.LocalDateTime

// SecurityConfig를 명시적으로 Import해 각 경로가 STUDENT Role을 실제로 요구하는지(403)까지
// 검증한다(NotificationControllerTest와 동일한 방식).
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
        private val firstPage = PageRequest.of(0, 20)

        // Pageable은 Kotlin 비Null 참조 Type이라 Mockito가 Kotlin Metadata를 읽어 순수 any()에
        // null을 채우면 "any(...) must not be null"을 던진다 -- NotificationControllerTest와
        // 동일한 관례로 우회한다.
        private fun anyPageable(): Pageable = any(Pageable::class.java) ?: Pageable.unpaged()

        // registerExclusion의 exclusionType은 Kotlin 비Null Type이라 위와 같은 이유로 우회한다.
        private fun anyExclusionType(): ExclusionType = any(ExclusionType::class.java) ?: ExclusionType.THIS_JOB

        private fun studentAuth() =
            authentication(
                UsernamePasswordAuthenticationToken(memberId, null, listOf(SimpleGrantedAuthority("ROLE_STUDENT"))),
            )

        private fun teacherAuth() =
            authentication(
                UsernamePasswordAuthenticationToken(100L, null, listOf(SimpleGrantedAuthority("ROLE_TEACHER"))),
            )

        private fun jobResponseOf() =
            RecommendationJobResponse(
                jobId = 1L,
                title = "백엔드 개발 인턴",
                postingType = PostingType.GENERAL,
                applicationMethod = ApplicationMethod.INTERNAL,
                status = JobStatus.PUBLISHED,
                company = null,
                endDate = null,
                viewCount = 10,
                bookmarked = false,
            )

        private fun recommendationItemOf() =
            RecommendationItemResponse(
                recommendationId = 1L,
                job = jobResponseOf(),
                score = 82,
                suitabilityLevel = SuitabilityLevel.RECOMMENDED,
                rank = 1,
                reasons = emptyList(),
                generatedAt = LocalDateTime.of(2026, 8, 17, 6, 0),
            )

        // ---------- 조회 ----------

        @Test
        fun `학생은 자신의 추천 목록을 조회할 수 있다`() {
            val response =
                RecommendationListResponse.of(
                    enabled = true,
                    status = RecommendationStatus.READY,
                    generatedAt = LocalDateTime.of(2026, 8, 17, 6, 0),
                    nextGenerationAt = null,
                    page = PageImpl(listOf(recommendationItemOf()), firstPage, 1),
                )
            given(recommendationService.getMyRecommendations(memberId, null, firstPage)).willReturn(response)

            mockMvc
                .perform(get("/api/v1/me/job-recommendations").with(studentAuth()))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.content[0].job.jobId").value(1))
                .andExpect(jsonPath("$.data.content[0].score").value(82))
                .andExpect(jsonPath("$.data.content[0].suitabilityLevel").value("RECOMMENDED"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.first").value(true))
                .andExpect(jsonPath("$.data.last").value(true))
        }

        @Test
        fun `추천이 꺼져 있으면 DISABLED와 빈 목록을 반환한다`() {
            given(recommendationService.getMyRecommendations(memberId, null, firstPage))
                .willReturn(
                    RecommendationListResponse.of(
                        enabled = false,
                        status = RecommendationStatus.DISABLED,
                        generatedAt = null,
                        nextGenerationAt = null,
                        page = PageImpl(emptyList(), firstPage, 0),
                    ),
                )

            mockMvc
                .perform(get("/api/v1/me/job-recommendations").with(studentAuth()))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.status").value("DISABLED"))
                .andExpect(jsonPath("$.data.content").isEmpty())
        }

        @Test
        fun `suitabilityLevel Query Parameter를 그대로 전달한다`() {
            given(recommendationService.getMyRecommendations(memberId, SuitabilityLevel.HIGHLY_RECOMMENDED, firstPage))
                .willReturn(
                    RecommendationListResponse.of(
                        enabled = true,
                        status = RecommendationStatus.EMPTY,
                        generatedAt = null,
                        nextGenerationAt = null,
                        page = PageImpl(emptyList(), firstPage, 0),
                    ),
                )

            mockMvc
                .perform(
                    get("/api/v1/me/job-recommendations")
                        .param("suitabilityLevel", "HIGHLY_RECOMMENDED")
                        .with(studentAuth()),
                ).andExpect(status().isOk)

            verify(recommendationService).getMyRecommendations(memberId, SuitabilityLevel.HIGHLY_RECOMMENDED, firstPage)
        }

        @Test
        fun `인증 없이 조회하면 401이다`() {
            mockMvc
                .perform(get("/api/v1/me/job-recommendations"))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))

            verify(recommendationService, never()).getMyRecommendations(anyLong(), any(), anyPageable())
        }

        @Test
        fun `학생이 아니면 추천을 조회할 수 없다`() {
            mockMvc
                .perform(get("/api/v1/me/job-recommendations").with(teacherAuth()))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))

            verify(recommendationService, never()).getMyRecommendations(anyLong(), any(), anyPageable())
        }

        @Test
        fun `요청한 사용자 본인의 ID로만 조회한다`() {
            given(recommendationService.getMyRecommendations(anyLong(), any(), anyPageable()))
                .willReturn(
                    RecommendationListResponse.of(
                        enabled = false,
                        status = RecommendationStatus.DISABLED,
                        generatedAt = null,
                        nextGenerationAt = null,
                        page = PageImpl(emptyList(), firstPage, 0),
                    ),
                )

            mockMvc.perform(get("/api/v1/me/job-recommendations").with(studentAuth())).andExpect(status().isOk)

            val captor = ArgumentCaptor.forClass(Long::class.javaObjectType)
            verify(recommendationService).getMyRecommendations(captor.capture() ?: 0L, any(), anyPageable())
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
        fun `졸업생이 설정을 변경하면 400이다`() {
            willThrow(RecommendationNotEnrolledException())
                .given(recommendationService)
                .updateSetting(memberId, true)

            mockMvc
                .perform(
                    patch("/api/v1/recommendations/settings")
                        .with(studentAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "enabled": true }"""),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("NOT_ENROLLED"))
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

        // ---------- 관심 없음 등록 ----------

        @Test
        fun `공고를 관심 없음으로 등록하면 201과 Resource를 반환한다`() {
            given(recommendationService.registerExclusion(memberId, 1L, ExclusionType.THIS_JOB))
                .willReturn(
                    RecommendationExclusionResponse(
                        exclusionId = 10L,
                        job = jobResponseOf(),
                        exclusionType = ExclusionType.THIS_JOB,
                        createdAt = LocalDateTime.of(2026, 8, 17, 6, 0),
                    ),
                )

            mockMvc
                .perform(
                    post("/api/v1/me/recommendation-exclusions")
                        .with(studentAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "jobId": 1, "exclusionType": "THIS_JOB" }"""),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.data.exclusionId").value(10))
                .andExpect(jsonPath("$.data.exclusionType").value("THIS_JOB"))
        }

        @Test
        fun `SIMILAR_JOBS도 등록할 수 있다`() {
            given(recommendationService.registerExclusion(memberId, 1L, ExclusionType.SIMILAR_JOBS))
                .willReturn(
                    RecommendationExclusionResponse(
                        exclusionId = 11L,
                        job = jobResponseOf(),
                        exclusionType = ExclusionType.SIMILAR_JOBS,
                        createdAt = LocalDateTime.of(2026, 8, 17, 6, 0),
                    ),
                )

            mockMvc
                .perform(
                    post("/api/v1/me/recommendation-exclusions")
                        .with(studentAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "jobId": 1, "exclusionType": "SIMILAR_JOBS" }"""),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.data.exclusionType").value("SIMILAR_JOBS"))
        }

        @Test
        fun `관심 없음 대상 공고가 없으면 404다`() {
            willThrow(RecommendationJobNotFoundException(999L))
                .given(recommendationService)
                .registerExclusion(memberId, 999L, ExclusionType.THIS_JOB)

            mockMvc
                .perform(
                    post("/api/v1/me/recommendation-exclusions")
                        .with(studentAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "jobId": 999, "exclusionType": "THIS_JOB" }"""),
                ).andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("JOB_NOT_FOUND"))
        }

        @Test
        fun `이미 관심 없음으로 설정된 공고를 다시 등록하면 409다`() {
            willThrow(RecommendationExclusionAlreadyExistsException(1L))
                .given(recommendationService)
                .registerExclusion(memberId, 1L, ExclusionType.THIS_JOB)

            mockMvc
                .perform(
                    post("/api/v1/me/recommendation-exclusions")
                        .with(studentAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "jobId": 1, "exclusionType": "THIS_JOB" }"""),
                ).andExpect(status().isConflict)
                .andExpect(jsonPath("$.error.code").value("EXCLUSION_ALREADY_EXISTS"))
        }

        @Test
        fun `필수 값이 없으면 관심 없음 등록도 400이다`() {
            mockMvc
                .perform(
                    post("/api/v1/me/recommendation-exclusions")
                        .with(studentAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ }"""),
                ).andExpect(status().isBadRequest)

            verify(recommendationService, never()).registerExclusion(anyLong(), anyLong(), anyExclusionType())
        }

        @Test
        fun `인증 없이 관심 없음을 등록하면 401이다`() {
            mockMvc
                .perform(
                    post("/api/v1/me/recommendation-exclusions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "jobId": 1, "exclusionType": "THIS_JOB" }"""),
                ).andExpect(status().isUnauthorized)

            verify(recommendationService, never()).registerExclusion(anyLong(), anyLong(), anyExclusionType())
        }

        @Test
        fun `학생이 아니면 관심 없음을 등록할 수 없다`() {
            mockMvc
                .perform(
                    post("/api/v1/me/recommendation-exclusions")
                        .with(teacherAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "jobId": 1, "exclusionType": "THIS_JOB" }"""),
                ).andExpect(status().isForbidden)
        }

        // ---------- 관심 없음 목록 ----------

        @Test
        fun `학생은 자신의 관심 없음 목록을 조회할 수 있다`() {
            val response =
                RecommendationExclusionListResponse.of(
                    PageImpl(
                        listOf(
                            RecommendationExclusionResponse(
                                exclusionId = 10L,
                                job = jobResponseOf(),
                                exclusionType = ExclusionType.THIS_JOB,
                                createdAt = LocalDateTime.of(2026, 8, 17, 6, 0),
                            ),
                        ),
                        firstPage,
                        1,
                    ),
                )
            given(recommendationService.listExclusions(memberId, null, firstPage)).willReturn(response)

            mockMvc
                .perform(get("/api/v1/me/recommendation-exclusions").with(studentAuth()))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.content[0].exclusionId").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(1))
        }

        @Test
        fun `exclusionType Query Parameter를 그대로 전달한다`() {
            given(recommendationService.listExclusions(memberId, ExclusionType.SIMILAR_JOBS, firstPage))
                .willReturn(RecommendationExclusionListResponse.of(PageImpl(emptyList(), firstPage, 0)))

            mockMvc
                .perform(
                    get("/api/v1/me/recommendation-exclusions")
                        .param("exclusionType", "SIMILAR_JOBS")
                        .with(studentAuth()),
                ).andExpect(status().isOk)

            verify(recommendationService).listExclusions(memberId, ExclusionType.SIMILAR_JOBS, firstPage)
        }

        @Test
        fun `인증 없이 관심 없음 목록을 조회하면 401이다`() {
            mockMvc
                .perform(get("/api/v1/me/recommendation-exclusions"))
                .andExpect(status().isUnauthorized)

            verify(recommendationService, never()).listExclusions(anyLong(), any(), anyPageable())
        }

        @Test
        fun `학생이 아니면 관심 없음 목록을 조회할 수 없다`() {
            mockMvc
                .perform(get("/api/v1/me/recommendation-exclusions").with(teacherAuth()))
                .andExpect(status().isForbidden)
        }

        // ---------- 관심 없음 해제 ----------

        @Test
        fun `관심 없음을 해제하면 204다`() {
            mockMvc
                .perform(delete("/api/v1/me/recommendation-exclusions/10").with(studentAuth()))
                .andExpect(status().isNoContent)

            verify(recommendationService).removeExclusion(memberId, 10L)
        }

        @Test
        fun `해제 대상이 없으면 404다`() {
            willThrow(RecommendationExclusionNotFoundException(999L))
                .given(recommendationService)
                .removeExclusion(memberId, 999L)

            mockMvc
                .perform(delete("/api/v1/me/recommendation-exclusions/999").with(studentAuth()))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("EXCLUSION_NOT_FOUND"))
        }

        @Test
        fun `다른 사용자의 관심 없음을 해제하려 하면 404다`() {
            willThrow(RecommendationExclusionNotFoundException(10L))
                .given(recommendationService)
                .removeExclusion(memberId, 10L)

            mockMvc
                .perform(delete("/api/v1/me/recommendation-exclusions/10").with(studentAuth()))
                .andExpect(status().isNotFound)
        }

        @Test
        fun `인증 없이 관심 없음을 해제하면 401이다`() {
            mockMvc
                .perform(delete("/api/v1/me/recommendation-exclusions/10"))
                .andExpect(status().isUnauthorized)

            verify(recommendationService, never()).removeExclusion(anyLong(), anyLong())
        }

        @Test
        fun `학생이 아니면 관심 없음을 해제할 수 없다`() {
            mockMvc
                .perform(delete("/api/v1/me/recommendation-exclusions/10").with(teacherAuth()))
                .andExpect(status().isForbidden)
        }
    }
