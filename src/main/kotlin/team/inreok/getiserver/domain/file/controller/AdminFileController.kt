package team.inreok.getiserver.domain.file.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.file.dto.AdminFileListResponse
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.entity.type.FileStatus
import team.inreok.getiserver.domain.file.service.AdminFileQueryService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

/**
 * 관리자 공통 파일 목록·사용처 조회 API다(Issue #225). `admin-common-file-management` 화면이
 * Mock 없이 실제 [team.inreok.getiserver.domain.file.entity.StoredFile] 목록을 표시할 수 있게
 * 한다. 업로드·다운로드는 기존 [FileController]를 그대로 재사용하고, 이 Controller는 조회만
 * 제공한다.
 *
 * `SecurityConfig`가 `/api/v1/admin/files`를 DEVELOPER 역할까지만 허용한다 -- 파일 업로더·연결
 * 대상 등 운영 정보를 포함하므로 `/api/v1/admin/discord-deliveries`(Issue #206)와 같은 기준을
 * 따른다.
 */
@Tag(
    name = "File - 파일",
    description = "공통 파일 업로드·다운로드. 필요 권한: 인증된 사용자(학생, 교사, 개발자).",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
@RestController
@RequestMapping("/api/v1/admin/files")
class AdminFileController(
    private val adminFileQueryService: AdminFileQueryService,
) {
    @Operation(
        summary = "관리자 파일 목록 조회",
        description = """
            업로드된 모든 파일을 최신 업로드순으로 조회한다. 개발자만 접근할 수 있다. 모든 Filter는
            함께 조합할 수 있고(AND), 지정하지 않은 Filter는 적용하지 않는다. `status`/`purpose`는
            일반 사용자 조회와 달리 PENDING/FAILED/DELETED를 포함한 모든 상태를 대상으로 한다 --
            운영 화면은 업로드 중간 상태·고아 파일까지 확인할 수 있어야 한다. `originalName`은 원본
            파일명을 대소문자 구분 없이 부분 검색한다. 기본 page=0, size=20이며 최대 size=100이다.

            `ownerType`/`ownerId`가 이 파일이 연결된 리소스(사용처)의 종류와 ID다. 아직 어떤
            리소스에도 연결되지 않았으면(PENDING/UPLOADED/FAILED/DELETED) 둘 다 null이다. 대상의
            표시 이름(예: 공고 제목)은 이 API가 제공하지 않는다.

            `uploader`는 업로더 회원 ID와 이름만 담는다(email/phone 같은 민감 정보는 노출하지
            않는다). `objectKey`, Bucket, Storage 내부 경로, Credential, Presigned URL은 어떤
            Field로도 노출하지 않는다 -- 다운로드는 기존
            `GET /api/v1/files/{fileId}/download`를 그대로 사용한다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공(결과가 없으면 빈 목록)"),
        SwaggerApiResponse(
            responseCode = "400",
            description = "status 또는 purpose 값이 올바르지 않음 (TYPE_MISMATCH)",
        ),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "개발자 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping
    fun listFiles(
        @Parameter(description = "파일 상태 필터(선택). 생략하면 전체 상태를 조회한다.")
        @RequestParam(required = false)
        status: FileStatus?,
        @Parameter(description = "업로드 목적 필터(선택). 생략하면 전체를 조회한다.")
        @RequestParam(required = false)
        purpose: FilePurpose?,
        @Parameter(description = "원본 파일명 부분 검색(선택, 대소문자 무시)")
        @RequestParam(required = false)
        originalName: String?,
        @Parameter(description = "Pagination(page: 0부터 시작, size: 기본 20, 최대 100). sort는 무시된다.")
        pageable: Pageable,
    ): ApiResponse<AdminFileListResponse> =
        ApiResponse.of(adminFileQueryService.list(status, purpose, originalName, pageable))
}
