package team.inreok.getiserver.domain.member.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.member.dto.MemberProfileUpdateResponse
import team.inreok.getiserver.domain.member.dto.MyProfileResponse
import team.inreok.getiserver.domain.member.entity.type.DepartmentType
import team.inreok.getiserver.domain.member.service.MemberService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import tools.jackson.databind.JsonNode
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Member - 내 프로필", description = "로그인한 본인의 프로필을 조회·수정한다. 필요 권한: 학생, 교사, 개발자.")
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
// SecurityConfig가 이 경로를 인증 필수로 지정하므로, 여기 도달했다는 것은 이미 유효한
// Access Token(JwtAuthenticationFilter)이 SecurityContext에 인증 정보를 채웠다는 뜻이다.
@RestController
@RequestMapping("/api/v1/me/profile")
class MemberProfileController(
    private val memberService: MemberService,
) {
    @Operation(
        summary = "내 프로필 조회",
        description =
            "Access Token으로 식별한 로그인한 본인의 전체 프로필(이름, 이메일, 역할, 학적 상태, " +
                "전공, 기술스택 등)을 조회한다.",
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "404", description = "내 프로필을 찾을 수 없음 (PROFILE_NOT_FOUND)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping
    fun getMyProfile(authentication: Authentication): ApiResponse<MyProfileResponse> {
        val memberId = authentication.principal as Long
        return ApiResponse.of(memberService.getMyProfile(memberId))
    }

    @Operation(
        summary = "내 프로필 부분 수정",
        description = """
            요청 Body에 포함된 Field만 수정한다(부분 수정, PATCH). Field를 아예 보내지 않으면 값을
            그대로 유지하고, `null`을 명시적으로 보내면 값을 지운다. 허용되지 않은 Field가 포함되면
            요청 전체를 거부한다.

            프로필 이미지는 `profileImageFileId`로 지정한다 — `POST /api/v1/files`에
            purpose=PROFILE_IMAGE로 업로드해 받은 File ID다. 본인이 올린 파일만 연결할 수 있고,
            이미 등록된 이미지가 있으면 자동으로 교체된다. `null`을 보내면 이미지를 제거한다.
            URL 문자열을 받는 `profileImageUrl`은 요청 Field로 지원하지 않으며 보내면
            PROFILE_VALIDATION_FAILED로 거부된다(응답 Field로는 계속 존재한다).

            `links`(블로그, 포트폴리오 등 추가 링크 목록)는 다른 Field와 달리 배열 자체로 세 가지
            상태를 구분한다 — `links`를 아예 보내지 않으면 기존 목록을 유지하고, 빈 배열(`[]`)을
            보내면 전체 삭제하며, 값이 있는 배열을 보내면 배열 순서(표시 순서)대로 전체 교체한다.
            최대 20개까지 등록할 수 있고 각 원소는 `label`(최대 100자)과 `url`(최대 2000자,
            http/https만 허용)을 모두 포함해야 한다. 기존 `githubUrl`은 `links`와 별도로 유지되는
            독립된 Field라 `links`를 보내도 `githubUrl` 값에는 영향을 주지 않는다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "수정 성공, 수정된 전체 필드를 반환"),
        SwaggerApiResponse(
            responseCode = "400",
            description =
                "요청 값 형식 오류, 허용되지 않은 Field 포함, department 값 오류, " +
                    "profileImageUrl 미지원, links 형식/개수/길이/scheme 오류 (PROFILE_VALIDATION_FAILED). " +
                    "profileImageFileId가 본인 파일이 아니면 403 FILE_NOT_OWNED, " +
                    "용도가 다르면 400 FILE_PURPOSE_MISMATCH로 거부된다.",
        ),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "본인이 업로드하지 않은 파일을 연결하려 함 (FILE_NOT_OWNED)"),
        SwaggerApiResponse(
            responseCode = "404",
            description = "회원을 찾을 수 없음(PROFILE_NOT_FOUND), 파일이 없거나 사용할 수 없는 상태(FILE_NOT_FOUND)",
        ),
        SwaggerApiResponse(responseCode = "409", description = "이미 다른 리소스에 연결된 파일 (FILE_ALREADY_LINKED)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @SwaggerRequestBody(
        required = true,
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = MemberProfileUpdateRequestExample::class),
                examples = [
                    ExampleObject(name = "bio만 수정", value = """{ "bio": "안녕하세요, 백엔드에 관심 있는 학생입니다." }"""),
                    ExampleObject(name = "phone을 명시적으로 삭제", value = """{ "phone": null }"""),
                    ExampleObject(
                        name = "links 전체 교체",
                        value =
                            """
                            {
                              "links": [
                                { "label": "기술 블로그", "url": "https://blog.example.com/me" },
                                { "label": "포트폴리오", "url": "https://portfolio.example.com" }
                              ]
                            }
                            """,
                    ),
                    ExampleObject(name = "links 전체 삭제", value = """{ "links": [] }"""),
                ],
            ),
        ],
    )
    @PatchMapping
    fun updateMyProfile(
        authentication: Authentication,
        @RequestBody body: JsonNode,
    ): ApiResponse<MemberProfileUpdateResponse> {
        val memberId = authentication.principal as Long
        return ApiResponse.of(memberService.updateProfile(memberId, body))
    }
}

// PATCH 요청 Body는 고정 DTO가 아니라 JsonNode로 받는다("전달되지 않음"과 "명시적 null"을 구분하기
// 위함, MemberService.updateProfile 참고). Swagger에는 실제 허용 Field Schema를 보여주기 위한
// 문서 전용 Class이며 Production Class로 사용하지 않는다.
@Schema(name = "MemberProfileUpdateRequest", description = "내 프로필 부분 수정 요청. 전달하지 않은 Field는 유지되고, null은 값을 지운다.")
private data class MemberProfileUpdateRequestExample(
    @param:Schema(description = "학과", nullable = true, example = "SW_DEVELOPMENT")
    val department: DepartmentType?,
    @param:Schema(description = "전화번호(최대 30자)", nullable = true, example = "010-1234-5678", maxLength = 30)
    val phone: String?,
    @param:Schema(description = "희망 직무", nullable = true, example = "Backend Developer")
    val desiredJob: String?,
    @param:Schema(description = "자기소개(최대 1000자)", nullable = true, example = "안녕하세요.", maxLength = 1000)
    val bio: String?,
    @param:Schema(
        description = "GitHub URL(최대 500자). 하위 호환을 위해 유지하며 links와 별도로 관리된다.",
        nullable = true,
        example = "https://github.com/example",
        maxLength = 500,
    )
    val githubUrl: String?,
    @param:Schema(
        description =
            "블로그/포트폴리오 등 추가 링크 목록(최대 20개, label/url 각 100자/2000자 이내, url은 " +
                "http/https만 허용). 전달하지 않으면 기존 값을 유지하고, 빈 배열([])을 보내면 전체 " +
                "삭제하며, 값이 있는 배열을 보내면 배열 순서대로 전체 교체한다.",
        nullable = true,
    )
    val links: List<MemberProfileLinkRequestExample>?,
    @param:Schema(description = "프로필 공개 여부. 학생만 false로 설정할 수 있다.", nullable = true, example = "true")
    val isPublic: Boolean?,
    @param:Schema(
        description =
            "프로필 이미지 File ID. POST /api/v1/files에 purpose=PROFILE_IMAGE로 업로드해 받는다. " +
                "본인이 올린 파일만 연결할 수 있고, null을 보내면 현재 이미지를 제거한다.",
        nullable = true,
        example = "42",
    )
    val profileImageFileId: Long?,
    @param:Schema(
        description = "지원하지 않는 Field다. 값을 보내면 항상 400으로 거부된다. profileImageFileId를 사용한다.",
        nullable = true,
    )
    val profileImageUrl: String?,
)

// links 배열 원소 하나의 문서 전용 Schema. Production Class로 사용하지 않는다.
@Schema(name = "MemberProfileLinkRequest", description = "프로필 링크 하나(Label + URL). 배열 순서가 그대로 표시 순서가 된다.")
private data class MemberProfileLinkRequestExample(
    @param:Schema(description = "링크 표시 이름(최대 100자)", example = "기술 블로그", maxLength = 100)
    val label: String,
    @param:Schema(
        description = "링크 URL(최대 2000자, http/https만 허용)",
        example = "https://blog.example.com/me",
        maxLength = 2000,
    )
    val url: String,
)
