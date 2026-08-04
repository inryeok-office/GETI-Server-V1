package team.inreok.getiserver.domain.collector.controller

import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
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
import team.inreok.getiserver.domain.collector.dto.CollectionRunDetailResponse
import team.inreok.getiserver.domain.collector.dto.CollectionRunListResponse
import team.inreok.getiserver.domain.collector.dto.JobSourceListResponse
import team.inreok.getiserver.domain.collector.dto.JobSourceResponse
import team.inreok.getiserver.domain.collector.dto.JobSourceUpdateRequest
import team.inreok.getiserver.domain.collector.dto.JobSourceUpdateResponse
import team.inreok.getiserver.domain.collector.entity.CollectionRun
import team.inreok.getiserver.domain.collector.entity.type.CollectionRunStatus
import team.inreok.getiserver.domain.collector.entity.type.CollectorAction
import team.inreok.getiserver.domain.collector.entity.type.JobSourceApprovalStatus
import team.inreok.getiserver.domain.collector.entity.type.JobSourceCode
import team.inreok.getiserver.domain.collector.entity.type.JobSourceType
import team.inreok.getiserver.domain.collector.exception.CollectorAlreadyRunningException
import team.inreok.getiserver.domain.collector.exception.JobSourceNotFoundException
import team.inreok.getiserver.domain.collector.service.CollectionRunQueryService
import team.inreok.getiserver.domain.collector.service.CollectorExecutionService
import team.inreok.getiserver.domain.collector.service.JobSourceService
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig
import java.time.LocalDateTime

// SecurityConfig를 명시적으로 Import해 /api/v1/admin/job-sources 등이 실제로 DEVELOPER 권한을
// 요구하는지(401/403)까지 검증한다(JobAdminControllerTest와 동일한 방식).
@WebMvcTest(controllers = [CollectorAdminController::class])
@Import(SecurityConfig::class)
@EnableWebSecurity
class CollectorAdminControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var jobSourceService: JobSourceService

        @MockitoBean
        private lateinit var collectionRunQueryService: CollectionRunQueryService

        @MockitoBean
        private lateinit var collectorExecutionService: CollectorExecutionService

        @MockitoBean
        private lateinit var jwtTokenProvider: JwtTokenProvider

        private fun authOf(
            memberId: Long,
            vararg roles: String,
        ) = authentication(
            UsernamePasswordAuthenticationToken(memberId, null, roles.map { SimpleGrantedAuthority("ROLE_$it") }),
        )

        private fun anyPageable(): Pageable = any(Pageable::class.java) ?: PageRequest.of(0, 20)

        private fun anyUpdateRequest(): JobSourceUpdateRequest =
            any(JobSourceUpdateRequest::class.java) ?: JobSourceUpdateRequest(enabled = null)

        private fun jobSourceResponse() =
            JobSourceResponse(
                sourceId = 1L,
                sourceCode = JobSourceCode.MMA,
                name = "병역일터",
                sourceType = JobSourceType.EXTERNAL_API,
                approvalStatus = JobSourceApprovalStatus.PENDING_APPROVAL,
                enabled = false,
                configured = false,
                dailyRequestLimit = null,
                lastCollectedAt = null,
                lastSuccessAt = null,
                lastFailureAt = null,
                lastError = null,
            )

        // --- 수집원 목록 ---

        @Test
        fun `개발자가 수집원 목록을 조회하면 200을 반환한다`() {
            given(jobSourceService.list()).willReturn(JobSourceListResponse(sources = listOf(jobSourceResponse())))

            mockMvc
                .perform(get("/api/v1/admin/job-sources").with(authOf(1L, "DEVELOPER")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.sources[0].sourceCode").value("MMA"))
                .andExpect(jsonPath("$.data.sources[0].configured").value(false))
        }

        @Test
        fun `학생은 수집원 목록을 조회할 수 없고 403을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/admin/job-sources").with(authOf(1L, "STUDENT")))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
        }

        @Test
        fun `교사는 수집원 목록을 조회할 수 없고 403을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/admin/job-sources").with(authOf(1L, "TEACHER")))
                .andExpect(status().isForbidden)
        }

        @Test
        fun `인증 없이 수집원 목록을 조회하면 401을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/admin/job-sources"))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        }

        // --- 수집원 활성 상태 변경 ---

        @Test
        fun `개발자가 수집원을 활성화하면 200을 반환한다`() {
            given(jobSourceService.updateEnabled(anyLong(), anyUpdateRequest())).willReturn(
                JobSourceUpdateResponse(
                    sourceId = 1L,
                    sourceCode = JobSourceCode.MMA,
                    approvalStatus = JobSourceApprovalStatus.READY,
                    enabled = true,
                    configured = true,
                    updatedAt = LocalDateTime.of(2026, 8, 3, 9, 0),
                ),
            )

            mockMvc
                .perform(
                    patch("/api/v1/admin/job-sources/1")
                        .with(authOf(1L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "enabled": true }"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.enabled").value(true))
        }

        @Test
        fun `존재하지 않는 수집원을 변경하면 404를 반환한다`() {
            given(
                jobSourceService.updateEnabled(anyLong(), anyUpdateRequest()),
            ).willThrow(JobSourceNotFoundException(99L))

            mockMvc
                .perform(
                    patch("/api/v1/admin/job-sources/99")
                        .with(authOf(1L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "enabled": true }"""),
                ).andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("JOB_SOURCE_NOT_FOUND"))
        }

        @Test
        fun `이미 실행 중인 수집원을 활성화하면 409를 반환한다`() {
            given(
                jobSourceService.updateEnabled(anyLong(), anyUpdateRequest()),
            ).willThrow(CollectorAlreadyRunningException(1L))

            mockMvc
                .perform(
                    patch("/api/v1/admin/job-sources/1")
                        .with(authOf(1L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "enabled": true }"""),
                ).andExpect(status().isConflict)
                .andExpect(jsonPath("$.error.code").value("COLLECTOR_ALREADY_RUNNING"))
        }

        // --- 수동 실행 ---

        @Test
        fun `개발자가 수동 실행을 요청하면 202와 생성된 runId 목록을 반환한다`() {
            val run =
                CollectionRun(sourceId = 1L, action = CollectorAction.COLLECT, startedAt = LocalDateTime.now()).apply {
                    id =
                        10L
                }
            given(collectorExecutionService.triggerManual(CollectorAction.COLLECT, listOf(1L))).willReturn(listOf(run))

            mockMvc
                .perform(
                    post("/api/v1/admin/collector-actions")
                        .with(authOf(1L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "action": "COLLECT", "sourceIds": [1] }"""),
                ).andExpect(status().isAccepted)
                .andExpect(jsonPath("$.data.runIds[0]").value(10))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
        }

        @Test
        fun `존재하지 않는 수집원으로 수동 실행을 요청하면 404를 반환한다`() {
            given(collectorExecutionService.triggerManual(CollectorAction.COLLECT, listOf(99L)))
                .willThrow(JobSourceNotFoundException(99L))

            mockMvc
                .perform(
                    post("/api/v1/admin/collector-actions")
                        .with(authOf(1L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "action": "COLLECT", "sourceIds": [99] }"""),
                ).andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("JOB_SOURCE_NOT_FOUND"))
        }

        @Test
        fun `이미 실행 중인 수집원으로 수동 실행을 요청하면 409를 반환한다`() {
            given(collectorExecutionService.triggerManual(CollectorAction.SYNC, listOf(1L)))
                .willThrow(CollectorAlreadyRunningException(1L))

            mockMvc
                .perform(
                    post("/api/v1/admin/collector-actions")
                        .with(authOf(1L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "action": "SYNC", "sourceIds": [1] }"""),
                ).andExpect(status().isConflict)
                .andExpect(jsonPath("$.error.code").value("COLLECTOR_ALREADY_RUNNING"))
        }

        @Test
        fun `action이 없으면 400을 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/admin/collector-actions")
                        .with(authOf(1L, "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "sourceIds": [1] }"""),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `학생은 수동 실행을 요청할 수 없고 403을 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/admin/collector-actions")
                        .with(authOf(1L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "action": "COLLECT", "sourceIds": [1] }"""),
                ).andExpect(status().isForbidden)
        }

        // --- 수집 실행 목록·상세 ---

        @Test
        fun `개발자가 수집 실행 목록을 조회하면 200을 반환한다`() {
            given(collectionRunQueryService.search(any(), any(), any(), any(), anyPageable()))
                .willReturn(
                    CollectionRunListResponse(
                        content = emptyList(),
                        page = 0,
                        size = 20,
                        totalElements = 0,
                        totalPages = 0,
                        first = true,
                        last = true,
                    ),
                )

            mockMvc
                .perform(get("/api/v1/admin/collection-runs").with(authOf(1L, "DEVELOPER")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.content").isEmpty())
        }

        @Test
        fun `개발자가 수집 실행 상세를 조회하면 200을 반환한다`() {
            given(collectionRunQueryService.getDetail(1L)).willReturn(
                CollectionRunDetailResponse(
                    runId = 1L,
                    sourceId = 1L,
                    sourceName = "병역일터",
                    action = CollectorAction.SYNC,
                    status = CollectionRunStatus.SUCCESS,
                    successCount = 3,
                    failureCount = 0,
                    partialQualityCount = 1,
                    startedAt = LocalDateTime.of(2026, 8, 3, 3, 0),
                    finishedAt = LocalDateTime.of(2026, 8, 3, 3, 5),
                    totalCount = 3,
                    errors = emptyList(),
                ),
            )

            mockMvc
                .perform(get("/api/v1/admin/collection-runs/1").with(authOf(1L, "DEVELOPER")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.partialQualityCount").value(1))
        }

        @Test
        fun `학생은 수집 실행 이력을 조회할 수 없고 403을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/admin/collection-runs").with(authOf(1L, "STUDENT")))
                .andExpect(status().isForbidden)
        }
    }
