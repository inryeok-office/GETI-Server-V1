package team.inreok.getiserver.domain.file.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import team.inreok.getiserver.domain.file.dto.FileUploadResponse
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.service.FileUploadService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(
    name = "File - 파일",
    description = "공통 파일 업로드·다운로드. 필요 권한: 인증된 사용자(학생, 교사, 개발자).",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
// SecurityConfig가 /api/v1/files 이하를 인증 필수로 두므로, 여기 도달했다는 것은 인증을 이미
// 통과했다는 뜻이다. 로그인만으로 모든 파일을 받을 수 있다는 뜻은 아니며, 파일별 소유권과
// 대상 리소스 접근 권한은 Role로 알 수 없어 Service 계층이 별도로 판정한다(요구사항 §15/§16).
@RestController
@RequestMapping("/api/v1/files")
class FileController(
    private val fileUploadService: FileUploadService,
) {
    @Operation(
        summary = "파일 업로드",
        description = """
            파일 하나를 업로드하고 `fileId`를 돌려준다. 이 시점의 파일은 아직 어떤 리소스에도
            연결되지 않은 상태이며, 반환받은 `fileId`를 지원서·문의 등 각 리소스의 API에 전달해
            연결한다.

            서버는 클라이언트가 보낸 파일 이름과 `Content-Type`을 신뢰하지 않는다. 목적별 허용
            확장자·MIME·최대 크기를 검사하고, 파일 내용에서 실제 형식을 직접 탐지해 확장자와
            일치하는지까지 확인한다. 응답의 `contentType`은 클라이언트가 보낸 값이 아니라 서버가
            탐지한 값이다.

            목적별 최대 **개수**는 이 API가 아니라 리소스에 연결하는 시점에 검사한다 — 업로드
            시점에는 대상 리소스가 아직 없을 수 있기 때문이다.
        """,
        requestBody =
            io.swagger.v3.oas.annotations.parameters.RequestBody(
                required = true,
                content = [Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE)],
            ),
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "201", description = "업로드 성공"),
        SwaggerApiResponse(
            responseCode = "400",
            description =
                "빈 파일 (FILE_EMPTY), 파일 이름이 올바르지 않음 (INVALID_FILE_NAME), " +
                    "purpose 누락 (MISSING_REQUEST_PARAMETER), purpose 값이 올바르지 않음 (TYPE_MISMATCH)",
        ),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "413", description = "허용된 파일 크기 초과 (FILE_TOO_LARGE)"),
        SwaggerApiResponse(
            responseCode = "415",
            description =
                "허용되지 않은 확장자·형식 (FILE_TYPE_NOT_ALLOWED), " +
                    "확장자와 실제 파일 형식 불일치 (MIME_MISMATCH)",
        ),
        SwaggerApiResponse(responseCode = "500", description = "파일 저장소 오류 (FILE_STORAGE_ERROR) 또는 서버 내부 오류"),
    )
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun upload(
        authentication: Authentication,
        @Parameter(description = "업로드할 파일", required = true)
        @RequestPart("file")
        file: MultipartFile,
        @Parameter(
            description = "파일의 사용 목적. 목적에 따라 허용 확장자·MIME·최대 크기가 달라진다.",
            required = true,
            schema = Schema(implementation = FilePurpose::class),
        )
        @RequestParam("purpose")
        purpose: FilePurpose,
    ): ApiResponse<FileUploadResponse> {
        val memberId = authentication.principal as Long
        return ApiResponse.of(fileUploadService.upload(memberId, file, purpose))
    }
}
