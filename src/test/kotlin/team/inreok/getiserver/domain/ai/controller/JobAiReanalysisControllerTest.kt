package team.inreok.getiserver.domain.ai.controller

import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.ai.dto.AiReanalysisResponse
import team.inreok.getiserver.domain.ai.entity.type.AiStatus
import team.inreok.getiserver.domain.ai.exception.AiAlreadyProcessingException
import team.inreok.getiserver.domain.ai.exception.AiJobNotFoundException
import team.inreok.getiserver.domain.ai.exception.AiReanalysisLimitExceededException
import team.inreok.getiserver.domain.ai.service.AiAnalysisService
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig
import java.time.LocalDateTime

// SecurityConfig를 명시적으로 Import해 /api/v1/jobs/**가 실제로 인증을 요구하는지(401)까지
// 검증한다(JobControllerTest와 같은 방식).
@WebMvcTest(controllers = [JobAiReanalysisController::class])
@Import(SecurityConfig::class)
@EnableWebSecurity
class JobAiReanalysisControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var aiAnalysisService: AiAnalysisService

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

        @Test
        fun `재분석을 접수하면 202와 결과를 반환한다`() {
            given(aiAnalysisService.reanalyze(1L)).willReturn(reanalysisResponse())

            mockMvc
                .perform(post("/api/v1/jobs/1/ai-reanalysis").with(authOf(1L, "STUDENT")))
                .andExpect(status().isAccepted)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobId").value(1))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.isReanalysis").value(true))
                .andExpect(jsonPath("$.data.reanalysisCount").value(1))
                .andExpect(jsonPath("$.data.remainingReanalysisCount").value(2))
                .andExpect(jsonPath("$.data.canReanalyze").value(true))
        }

        @Test
        fun `게시되지 않은 공고를 재분석하면 404를 반환한다`() {
            willThrow(AiJobNotFoundException(1L)).given(aiAnalysisService).reanalyze(anyLong())

            mockMvc
                .perform(post("/api/v1/jobs/1/ai-reanalysis").with(authOf(1L, "TEACHER")))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("JOB_NOT_FOUND"))
        }

        @Test
        fun `이미 분석 중이면 409를 반환한다`() {
            willThrow(AiAlreadyProcessingException(1L)).given(aiAnalysisService).reanalyze(anyLong())

            mockMvc
                .perform(post("/api/v1/jobs/1/ai-reanalysis").with(authOf(1L, "DEVELOPER")))
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.error.code").value("AI_ALREADY_PROCESSING"))
        }

        @Test
        fun `재분석 횟수를 모두 사용했으면 429를 반환한다`() {
            willThrow(AiReanalysisLimitExceededException(1L, 3)).given(aiAnalysisService).reanalyze(anyLong())

            mockMvc
                .perform(post("/api/v1/jobs/1/ai-reanalysis").with(authOf(1L, "STUDENT")))
                .andExpect(status().isTooManyRequests)
                .andExpect(jsonPath("$.error.code").value("AI_REANALYSIS_LIMIT"))
        }

        @Test
        fun `인증 없이 재분석을 요청하면 401을 반환한다`() {
            mockMvc
                .perform(post("/api/v1/jobs/1/ai-reanalysis"))
                .andExpect(status().isUnauthorized)
        }

        private fun reanalysisResponse() =
            AiReanalysisResponse(
                jobId = 1L,
                status = AiStatus.PENDING,
                isReanalysis = true,
                reanalysisCount = 1,
                remainingReanalysisCount = 2,
                canReanalyze = true,
                requestedAt = LocalDateTime.of(2026, 8, 15, 10, 0),
            )
    }
