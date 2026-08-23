package team.inreok.getiserver.domain.file.controller

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.Pageable
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.file.dto.AdminFileListItemResponse
import team.inreok.getiserver.domain.file.dto.AdminFileListResponse
import team.inreok.getiserver.domain.file.dto.AdminFileUploaderResponse
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.entity.type.FileStatus
import team.inreok.getiserver.domain.file.service.AdminFileQueryService
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig
import team.inreok.getiserver.global.web.WebPageableConfig
import java.time.LocalDateTime

// SecurityConfig를 명시적으로 Import해 "/api/v1/admin/files"가 실제로 DEVELOPER Role까지
// 요구하는지 검증한다(DiscordDeliveryAdminControllerTest와 동일한 방식). WebPageableConfig도
// 함께 Import해야 최대 Page Size(100) 강제가 이 Slice에서도 동작한다.
@WebMvcTest(controllers = [AdminFileController::class])
@Import(team.inreok.getiserver.global.security.NormalSecurityTestConfig::class, WebPageableConfig::class)
@EnableWebSecurity
class AdminFileControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var adminFileQueryService: AdminFileQueryService

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

        private fun listItem() =
            AdminFileListItemResponse(
                fileId = 42L,
                originalName = "이력서.pdf",
                sizeBytes = 204_800,
                contentType = "application/pdf",
                purpose = FilePurpose.JOB_APPLICATION,
                status = FileStatus.LINKED,
                uploader = AdminFileUploaderResponse(memberId = 7L, name = "홍길동"),
                ownerType = FileOwnerType.JOB_APPLICATION,
                ownerId = 15L,
                createdAt = LocalDateTime.of(2026, 8, 20, 9, 0),
            )

        private fun listResponse(content: List<AdminFileListItemResponse> = listOf(listItem())) =
            AdminFileListResponse(
                content = content,
                page = 0,
                size = 20,
                totalElements = content.size.toLong(),
                totalPages = 1,
                first = true,
                last = true,
            )

        // Mockito any()는 Kotlin non-null Pageable Parameter에 null을 돌려주므로 Elvis로
        // Fallback을 둔다(DiscordDeliveryAdminControllerTest.anyPageable과 동일한 관례).
        private fun anyPageable(): Pageable = any(Pageable::class.java) ?: Pageable.unpaged()

        @Test
        fun `개발자는 파일 목록을 조회할 수 있다`() {
            given(adminFileQueryService.list(any(), any(), any(), anyPageable())).willReturn(listResponse())

            mockMvc
                .perform(get("/api/v1/admin/files").with(authOf(1L, "DEVELOPER")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.content[0].fileId").value(42))
                .andExpect(jsonPath("$.data.content[0].originalName").value("이력서.pdf"))
                .andExpect(jsonPath("$.data.content[0].sizeBytes").value(204800))
                .andExpect(jsonPath("$.data.content[0].uploader.memberId").value(7))
                .andExpect(jsonPath("$.data.content[0].uploader.name").value("홍길동"))
                .andExpect(jsonPath("$.data.content[0].ownerType").value("JOB_APPLICATION"))
                .andExpect(jsonPath("$.data.content[0].ownerId").value(15))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(1))
        }

        @Test
        fun `응답에는 objectKey Bucket 등 Storage 내부 정보가 담기지 않는다`() {
            given(adminFileQueryService.list(any(), any(), any(), anyPageable())).willReturn(listResponse())

            mockMvc
                .perform(get("/api/v1/admin/files").with(authOf(1L, "DEVELOPER")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.content[0].objectKey").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].bucket").doesNotExist())
        }

        @Test
        fun `교사는 파일 목록을 조회할 수 없고 403을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/admin/files").with(authOf(1L, "TEACHER")))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
        }

        @Test
        fun `학생은 파일 목록을 조회할 수 없고 403을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/admin/files").with(authOf(1L, "STUDENT")))
                .andExpect(status().isForbidden)
        }

        @Test
        fun `인증 없이 파일 목록을 조회하면 401을 반환한다`() {
            mockMvc
                .perform(get("/api/v1/admin/files"))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `Query Parameter는 그대로 Service에 전달된다`() {
            given(adminFileQueryService.list(any(), any(), any(), anyPageable())).willReturn(listResponse())

            mockMvc
                .perform(
                    get("/api/v1/admin/files")
                        .param("status", "LINKED")
                        .param("purpose", "JOB_APPLICATION")
                        .param("originalName", "이력서")
                        .with(authOf(1L, "DEVELOPER")),
                ).andExpect(status().isOk)

            verify(adminFileQueryService).list(
                eq(FileStatus.LINKED),
                eq(FilePurpose.JOB_APPLICATION),
                eq("이력서"),
                anyPageable(),
            )
        }

        @Test
        fun `Filter를 생략하면 모두 null로 전달된다`() {
            given(adminFileQueryService.list(any(), any(), any(), anyPageable())).willReturn(listResponse())

            mockMvc
                .perform(get("/api/v1/admin/files").with(authOf(1L, "DEVELOPER")))
                .andExpect(status().isOk)

            verify(adminFileQueryService).list(isNull(), isNull(), isNull(), anyPageable())
        }

        @Test
        fun `status 값이 올바르지 않으면 400 TYPE_MISMATCH를 반환한다`() {
            mockMvc
                .perform(
                    get("/api/v1/admin/files")
                        .param("status", "NOT_A_STATUS")
                        .with(authOf(1L, "DEVELOPER")),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("TYPE_MISMATCH"))
        }

        @Test
        fun `목록 size가 최대값을 넘으면 100으로 잘린다`() {
            given(adminFileQueryService.list(any(), any(), any(), anyPageable())).willReturn(listResponse())

            mockMvc
                .perform(
                    get("/api/v1/admin/files")
                        .param("size", "500")
                        .with(authOf(1L, "DEVELOPER")),
                ).andExpect(status().isOk)

            val pageableCaptor = ArgumentCaptor.forClass(Pageable::class.java)
            verify(adminFileQueryService).list(any(), any(), any(), pageableCaptor.capture() ?: Pageable.unpaged())
            assertThat(pageableCaptor.value.pageSize).isEqualTo(100)
        }
    }
