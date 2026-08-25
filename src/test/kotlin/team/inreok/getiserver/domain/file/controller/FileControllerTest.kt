package team.inreok.getiserver.domain.file.controller

import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.multipart.MultipartFile
import team.inreok.getiserver.domain.file.dto.FileUploadResponse
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.exception.FileAccessDeniedException
import team.inreok.getiserver.domain.file.exception.FileNotFoundException
import team.inreok.getiserver.domain.file.exception.FileTooLargeException
import team.inreok.getiserver.domain.file.exception.FileTypeNotAllowedException
import team.inreok.getiserver.domain.file.exception.MimeMismatchException
import team.inreok.getiserver.domain.file.service.FileDownloadService
import team.inreok.getiserver.domain.file.service.FileUploadService
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig
import java.net.URI
import java.time.LocalDateTime

// SecurityConfig를 명시적으로 Import해 /api/v1/files 이하가 실제로 인증을 요구하는지(401)까지
// 검증한다(NotificationControllerTest와 동일한 방식).
@WebMvcTest(controllers = [FileController::class])
@Import(team.inreok.getiserver.global.security.NormalSecurityTestConfig::class)
@EnableWebSecurity
class FileControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var fileUploadService: FileUploadService

        @MockitoBean
        private lateinit var fileDownloadService: FileDownloadService

        @MockitoBean
        private lateinit var jwtTokenProvider: JwtTokenProvider

        private fun authAs(role: String) =
            authentication(
                UsernamePasswordAuthenticationToken(
                    MEMBER_ID,
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_$role")),
                ),
            )

        private fun pngPart(name: String = "logo.png") =
            MockMultipartFile("file", name, MediaType.IMAGE_PNG_VALUE, byteArrayOf(1, 2, 3))

        private fun uploadResponse() =
            FileUploadResponse(
                fileId = FILE_ID,
                originalName = "logo.png",
                contentType = "image/png",
                size = 3,
                purpose = FilePurpose.PROFILE_IMAGE,
                createdAt = LocalDateTime.of(2026, 8, 7, 10, 0),
            )

        // Mockito Matcher는 null을 돌려주는데 Service Method의 Parameter는 Kotlin non-null이라
        // 그대로 넘기면 호출 시점에 NPE가 난다. 저장소의 anyPageable() 선례와 같은 방식으로 감싼다.
        private fun anyMultipart(): MultipartFile = any(MultipartFile::class.java) ?: pngPart()

        private fun anyPurpose(): FilePurpose = any(FilePurpose::class.java) ?: FilePurpose.PROFILE_IMAGE

        private fun givenUploadSucceeds() {
            given(fileUploadService.upload(anyLong(), anyMultipart(), anyPurpose()))
                .willReturn(uploadResponse())
        }

        private fun givenUploadFails(ex: RuntimeException) {
            given(fileUploadService.upload(anyLong(), anyMultipart(), anyPurpose()))
                .willThrow(ex)
        }

        @Test
        fun `인증이 없으면 401이다`() {
            mockMvc
                .perform(multipart("/api/v1/files").file(pngPart()).param("purpose", "PROFILE_IMAGE"))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        }

        @Test
        fun `학생 교사 개발자 모두 업로드할 수 있다`() {
            givenUploadSucceeds()

            listOf("STUDENT", "TEACHER", "DEVELOPER").forEach { role ->
                mockMvc
                    .perform(
                        multipart("/api/v1/files")
                            .file(pngPart())
                            .param("purpose", "PROFILE_IMAGE")
                            .with(authAs(role)),
                    ).andExpect(status().isCreated)
            }
        }

        @Test
        fun `업로드 성공은 201과 공통 ApiResponse 구조로 응답한다`() {
            givenUploadSucceeds()

            mockMvc
                .perform(
                    multipart("/api/v1/files")
                        .file(pngPart())
                        .param("purpose", "PROFILE_IMAGE")
                        .with(authAs("STUDENT")),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fileId").value(FILE_ID))
                .andExpect(jsonPath("$.data.originalName").value("logo.png"))
                .andExpect(jsonPath("$.data.contentType").value("image/png"))
                .andExpect(jsonPath("$.data.purpose").value("PROFILE_IMAGE"))
                .andExpect(jsonPath("$.meta.requestId").exists())
        }

        @Test
        fun `응답에 Storage 내부 정보를 노출하지 않는다`() {
            givenUploadSucceeds()

            mockMvc
                .perform(
                    multipart("/api/v1/files")
                        .file(pngPart())
                        .param("purpose", "PROFILE_IMAGE")
                        .with(authAs("STUDENT")),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.data.objectKey").doesNotExist())
                .andExpect(jsonPath("$.data.bucket").doesNotExist())
                .andExpect(jsonPath("$.data.status").doesNotExist())
                .andExpect(jsonPath("$.data.uploaderMemberId").doesNotExist())
        }

        @Test
        fun `file이 없으면 400이다`() {
            mockMvc
                .perform(multipart("/api/v1/files").param("purpose", "PROFILE_IMAGE").with(authAs("STUDENT")))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.success").value(false))
        }

        @Test
        fun `purpose가 없으면 400 MISSING_REQUEST_PARAMETER다`() {
            mockMvc
                .perform(multipart("/api/v1/files").file(pngPart()).with(authAs("STUDENT")))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("MISSING_REQUEST_PARAMETER"))
        }

        @Test
        fun `purpose 값이 올바르지 않으면 400 TYPE_MISMATCH다`() {
            mockMvc
                .perform(
                    multipart("/api/v1/files")
                        .file(pngPart())
                        .param("purpose", "NOT_A_PURPOSE")
                        .with(authAs("STUDENT")),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("TYPE_MISMATCH"))
        }

        @Test
        fun `크기 초과는 413 FILE_TOO_LARGE다`() {
            givenUploadFails(FileTooLargeException(sizeBytes = 100, maxSizeBytes = 50))

            mockMvc
                .perform(
                    multipart("/api/v1/files")
                        .file(pngPart())
                        .param("purpose", "PROFILE_IMAGE")
                        .with(authAs("STUDENT")),
                ).andExpect(status().isPayloadTooLarge)
                .andExpect(jsonPath("$.error.code").value("FILE_TOO_LARGE"))
        }

        @Test
        fun `허용되지 않은 형식은 415 FILE_TYPE_NOT_ALLOWED다`() {
            givenUploadFails(FileTypeNotAllowedException(FilePurpose.PROFILE_IMAGE, "확장자=exe"))

            mockMvc
                .perform(
                    multipart("/api/v1/files")
                        .file(pngPart("malware.exe"))
                        .param("purpose", "PROFILE_IMAGE")
                        .with(authAs("STUDENT")),
                ).andExpect(status().isUnsupportedMediaType)
                .andExpect(jsonPath("$.error.code").value("FILE_TYPE_NOT_ALLOWED"))
        }

        @Test
        fun `확장자와 실제 형식이 다르면 415 MIME_MISMATCH다`() {
            givenUploadFails(MimeMismatchException("pdf", "image/png"))

            mockMvc
                .perform(
                    multipart("/api/v1/files")
                        .file(pngPart("resume.pdf"))
                        .param("purpose", "JOB_APPLICATION")
                        .with(authAs("STUDENT")),
                ).andExpect(status().isUnsupportedMediaType)
                .andExpect(jsonPath("$.error.code").value("MIME_MISMATCH"))
        }

        @Test
        fun `다운로드는 인증이 없으면 401이다`() {
            mockMvc
                .perform(get("/api/v1/files/$FILE_ID/download"))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `다운로드는 302와 Location Header로 응답한다`() {
            given(fileDownloadService.createDownloadUrl(anyLong(), anyLong(), anyBoolean()))
                .willReturn(URI.create(PRESIGNED_URL))

            mockMvc
                .perform(get("/api/v1/files/$FILE_ID/download").with(authAs("STUDENT")))
                .andExpect(status().isFound)
                .andExpect(header().string("Location", PRESIGNED_URL))
        }

        @Test
        fun `존재하지 않는 파일은 404 FILE_NOT_FOUND다`() {
            given(fileDownloadService.createDownloadUrl(anyLong(), anyLong(), anyBoolean()))
                .willThrow(FileNotFoundException(FILE_ID))

            mockMvc
                .perform(get("/api/v1/files/$FILE_ID/download").with(authAs("STUDENT")))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("FILE_NOT_FOUND"))
        }

        @Test
        fun `권한이 없으면 403 FILE_ACCESS_DENIED다`() {
            given(fileDownloadService.createDownloadUrl(anyLong(), anyLong(), anyBoolean()))
                .willThrow(FileAccessDeniedException())

            mockMvc
                .perform(get("/api/v1/files/$FILE_ID/download").with(authAs("STUDENT")))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("FILE_ACCESS_DENIED"))
        }

        private companion object {
            private const val MEMBER_ID = 1L
            private const val FILE_ID = 42L
            private const val PRESIGNED_URL = "https://storage.example.com/PROFILE_IMAGE/2026/08/uuid?sig=abc"
        }
    }
