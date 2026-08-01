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
            요청 전체를 거부한다. `profileImageUrl`은 응답 Field로는 존재하지만 File 업로드 연동 전이라
            요청으로 값을 보내면 항상 거부된다(PROFILE_VALIDATION_FAILED).
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "수정 성공, 수정된 전체 필드를 반환"),
        SwaggerApiResponse(
            responseCode = "400",
            description =
                "요청 값 형식 오류, 허용되지 않은 Field 포함, department 값 오류, " +
                    "profileImageUrl 미지원 (PROFILE_VALIDATION_FAILED)",
        ),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "404", description = "회원을 찾을 수 없음 (PROFILE_NOT_FOUND)"),
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
        description = "GitHub URL(최대 500자)",
        nullable = true,
        example = "https://github.com/example",
        maxLength = 500,
    )
    val githubUrl: String?,
    @param:Schema(description = "프로필 공개 여부. 학생만 false로 설정할 수 있다.", nullable = true, example = "true")
    val isPublic: Boolean?,
    @param:Schema(
        description = "아직 지원하지 않는 Field다. 값을 보내면 항상 400으로 거부된다(File 업로드 연동 전).",
        nullable = true,
    )
    val profileImageUrl: String?,
)
