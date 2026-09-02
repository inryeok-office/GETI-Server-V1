package team.inreok.getiserver.domain.application.controller

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.BDDMockito.willAnswer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.application.dto.ApplicationExportMaterialType
import team.inreok.getiserver.domain.application.exception.ApplicationReviewForbiddenException
import team.inreok.getiserver.domain.application.exception.JobNotFoundException
import team.inreok.getiserver.domain.application.service.JobApplicationExport
import team.inreok.getiserver.domain.application.service.JobApplicationExportService
import team.inreok.getiserver.domain.file.archive.FileArchiveEntry
import team.inreok.getiserver.domain.file.exception.FileArchiveEmptyException
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig
import java.io.ByteArrayOutputStream
import java.io.OutputStream

@WebMvcTest(controllers = [JobApplicationExportController::class])
@Import(team.inreok.getiserver.global.security.NormalSecurityTestConfig::class)
@EnableWebSecurity
class JobApplicationExportControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var jobApplicationExportService: JobApplicationExportService

        @MockitoBean
        private lateinit var jwtTokenProvider: JwtTokenProvider

        private fun authOf(
            memberId: Long,
            vararg roles: String,
        ) = authentication(
            UsernamePasswordAuthenticationToken(memberId, null, roles.map { SimpleGrantedAuthority("ROLE_$it") }),
        )

        // Kotlin non-null 파라미터(OutputStream)에 bare any()를 쓰면 null 반환으로 NPE가 나므로
        // Elvis로 Fallback을 둔다(JobApplicationAdminControllerTest.anyPageable과 동일한 관례).
        private fun anyOutputStream(): OutputStream = any(OutputStream::class.java) ?: ByteArrayOutputStream()

        private fun anyExport(): JobApplicationExport =
            any(JobApplicationExport::class.java) ?: JobApplicationExport(emptyList(), emptyList())

        private fun anyMaterialTypes(): Set<ApplicationExportMaterialType> =
            any(Set::class.java) as? Set<ApplicationExportMaterialType> ?: emptySet()

        @Test
        fun `담당 교사는 ZIP을 내려받는다`() {
            val entries = listOf(FileArchiveEntry(1L, "홍길동_resume.pdf"))
            given(
                jobApplicationExportService.buildExportMaterials(
                    1L,
                    100L,
                    false,
                    null,
                    setOf(ApplicationExportMaterialType.ATTACHMENTS),
                ),
            ).willReturn(JobApplicationExport(entries, emptyList()))
            willAnswer { invocation ->
                val output = invocation.getArgument<OutputStream>(1)
                output.write("PK".toByteArray())
                null
            }.given(jobApplicationExportService).writeZip(anyExport(), anyOutputStream())

            val result =
                mockMvc
                    .perform(get("/api/v1/admin/jobs/1/applications/export").with(authOf(100L, "TEACHER")))
                    .andExpect(status().isOk)
                    .andExpect(header().string("Content-Type", "application/zip"))
                    .andExpect(
                        header().string("Content-Disposition", "attachment; filename=\"job-1-applications.zip\""),
                    ).andReturn()

            assertThat(result.response.contentAsByteArray).isEqualTo("PK".toByteArray())
        }

        @Test
        fun `applicationIds Query Parameter를 지정하면 쉼표로 구분된 값이 List로 Binding되어 Service에 전달된다`() {
            // Issue #203 -- Controller의 Parameter Binding만 검증한다(다른 공고 소속 ID 무시 등
            // 정책 판단은 Service 책임이라 여기서는 다루지 않는다).
            val entries = listOf(FileArchiveEntry(1L, "홍길동_resume.pdf"))
            given(
                jobApplicationExportService.buildExportMaterials(
                    1L,
                    100L,
                    false,
                    listOf(1L, 2L, 3L),
                    setOf(ApplicationExportMaterialType.ATTACHMENTS),
                ),
            ).willReturn(JobApplicationExport(entries, emptyList()))
            willAnswer { invocation ->
                val output = invocation.getArgument<OutputStream>(1)
                output.write("PK".toByteArray())
                null
            }.given(jobApplicationExportService).writeZip(anyExport(), anyOutputStream())

            mockMvc
                .perform(
                    get("/api/v1/admin/jobs/1/applications/export")
                        .queryParam("applicationIds", "1,2,3")
                        .with(authOf(100L, "TEACHER")),
                ).andExpect(status().isOk)

            then(jobApplicationExportService).should().buildExportMaterials(
                1L,
                100L,
                false,
                listOf(1L, 2L, 3L),
                setOf(ApplicationExportMaterialType.ATTACHMENTS),
            )
        }

        @Test
        fun `applicationIds Query Parameter를 반복 지정해도 List로 Binding되어 Service에 전달된다`() {
            // Issue #203 -- "?applicationIds=1&applicationIds=2" 형태도 지원한다(Spring 기본 동작).
            val entries = listOf(FileArchiveEntry(1L, "홍길동_resume.pdf"))
            given(
                jobApplicationExportService.buildExportMaterials(
                    1L,
                    100L,
                    false,
                    listOf(1L, 2L),
                    setOf(ApplicationExportMaterialType.ATTACHMENTS),
                ),
            ).willReturn(JobApplicationExport(entries, emptyList()))
            willAnswer { invocation ->
                val output = invocation.getArgument<OutputStream>(1)
                output.write("PK".toByteArray())
                null
            }.given(jobApplicationExportService).writeZip(anyExport(), anyOutputStream())

            mockMvc
                .perform(
                    get("/api/v1/admin/jobs/1/applications/export")
                        .queryParam("applicationIds", "1", "2")
                        .with(authOf(100L, "TEACHER")),
                ).andExpect(status().isOk)

            then(jobApplicationExportService).should().buildExportMaterials(
                1L,
                100L,
                false,
                listOf(1L, 2L),
                setOf(ApplicationExportMaterialType.ATTACHMENTS),
            )
        }

        @Test
        fun `applicationIds Query Parameter를 지정하지 않으면 Service에 null이 전달된다(하위 호환)`() {
            // Issue #203 -- Client DownloadModal이 지원자를 개별 선택하지 않은 기존 호출부와 동일한 경로다.
            val entries = listOf(FileArchiveEntry(1L, "홍길동_resume.pdf"))
            given(
                jobApplicationExportService.buildExportMaterials(
                    1L,
                    100L,
                    false,
                    null,
                    setOf(ApplicationExportMaterialType.ATTACHMENTS),
                ),
            ).willReturn(JobApplicationExport(entries, emptyList()))
            willAnswer { invocation ->
                val output = invocation.getArgument<OutputStream>(1)
                output.write("PK".toByteArray())
                null
            }.given(jobApplicationExportService).writeZip(anyExport(), anyOutputStream())

            mockMvc
                .perform(get("/api/v1/admin/jobs/1/applications/export").with(authOf(100L, "TEACHER")))
                .andExpect(status().isOk)

            then(jobApplicationExportService).should().buildExportMaterials(
                1L,
                100L,
                false,
                null,
                setOf(ApplicationExportMaterialType.ATTACHMENTS),
            )
        }

        @Test
        fun `담당자가 아닌 교사는 403이다`() {
            given(
                jobApplicationExportService.buildExportMaterials(
                    1L,
                    999L,
                    false,
                    null,
                    setOf(ApplicationExportMaterialType.ATTACHMENTS),
                ),
            ).willThrow(ApplicationReviewForbiddenException())

            mockMvc
                .perform(get("/api/v1/admin/jobs/1/applications/export").with(authOf(999L, "TEACHER")))
                .andExpect(status().isForbidden)
        }

        @Test
        fun `존재하지 않는 공고는 404다`() {
            // Issue #203 -- applicationIds(List<Long>?) 추가로 4번째 Parameter도 Matcher가 필요하다.
            // Nullable Parameter라 any()의 null 반환이 NPE 없이 그대로 허용된다.
            given(
                jobApplicationExportService.buildExportMaterials(
                    anyLong(),
                    anyLong(),
                    anyBoolean(),
                    any(),
                    anyMaterialTypes(),
                ),
            ).willThrow(JobNotFoundException(999L))

            mockMvc
                .perform(get("/api/v1/admin/jobs/999/applications/export").with(authOf(100L, "TEACHER")))
                .andExpect(status().isNotFound)
        }

        @Test
        fun `writeZip이 아무것도 쓰지 않고 예외를 던지면 Content-Disposition을 남기지 않는다`() {
            // PR #157 코드리뷰 반영 -- Header를 미리 설정해두면 GlobalExceptionHandler가
            // response.reset() 없이 오류를 쓰기 때문에 Header가 오염된다(브라우저가 JSON 오류를
            // ZIP 첨부파일로 내려받음). Byte를 하나도 쓰지 않는 예외 경로에서는 Header 자체가
            // 없어야 한다.
            val entries = listOf(FileArchiveEntry(1L, "홍길동_resume.pdf"))
            given(
                jobApplicationExportService.buildExportMaterials(
                    1L,
                    100L,
                    false,
                    null,
                    setOf(ApplicationExportMaterialType.ATTACHMENTS),
                ),
            ).willReturn(JobApplicationExport(entries, emptyList()))
            org.mockito.BDDMockito
                .willThrow(FileArchiveEmptyException())
                .given(jobApplicationExportService)
                .writeZip(anyExport(), anyOutputStream())

            val result =
                mockMvc
                    .perform(get("/api/v1/admin/jobs/1/applications/export").with(authOf(100L, "TEACHER")))
                    .andExpect(status().isNotFound)
                    .andReturn()

            assertThat(result.response.getHeader("Content-Disposition")).isNull()
        }

        @Test
        fun `인증 없이 호출하면 401이다`() {
            mockMvc
                .perform(get("/api/v1/admin/jobs/1/applications/export"))
                .andExpect(status().isUnauthorized)
        }
    }
