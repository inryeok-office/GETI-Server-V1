package team.inreok.getiserver.domain.member.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.member.dto.MemberProfileResponse
import team.inreok.getiserver.domain.member.dto.MemberSearchResponse
import team.inreok.getiserver.domain.member.entity.type.AcademicStatus
import team.inreok.getiserver.domain.member.entity.type.DepartmentType
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.exception.ProfileViewForbiddenException
import team.inreok.getiserver.domain.member.service.MemberSearchService
import team.inreok.getiserver.domain.member.service.MemberService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Member - 학생 조회/검색", description = "다른 학생의 프로필을 조회하거나 이름으로 검색한다. 필요 권한: 학생, 교사, 개발자.")
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
// SecurityConfig가 이 경로를 인증 필수로 지정하므로, 여기 도달했다는 것은 이미 유효한
// Access Token(JwtAuthenticationFilter)이 SecurityContext에 인증 정보를 채웠다는 뜻이다.
@RestController
@RequestMapping("/api/v1/members")
class MemberController(
    private val memberService: MemberService,
    private val memberSearchService: MemberSearchService,
) {
    @Operation(
        summary = "학생 프로필 조회",
        description = """
            memberId로 지정한 학생의 프로필을 조회한다. 대상이 학생(STUDENT)이 아니면 404로 처리한다.
            대상 프로필이 비공개(isPublic=false)일 때 응답 범위는 요청자 Role에 따라 다르다.
            학생이 조회하면 이름, 프로필 이미지, 기수, 학과만 내려주고 전공/기술스택/희망직무/
            자기소개/links는 빈 값으로 응답한다(profileRestricted=true). 교사(TEACHER)와
            개발자(DEVELOPER)는 학생 관리·상담 목적으로 비공개 프로필도 전체를 받는다
            (profileRestricted=false).
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공(권한 없는 요청자가 본 비공개 프로필은 일부 Field가 빈 값)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(
            responseCode = "403",
            description = "요청자에게 학생·교사·개발자 Role이 없음 (PROFILE_VIEW_FORBIDDEN)",
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "회원이 없거나 학생이 아닌 회원(MEMBER_NOT_FOUND)",
        ),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping("/{memberId}")
    fun getProfile(
        authentication: Authentication,
        @Parameter(description = "조회할 학생의 회원 ID", example = "1") @PathVariable memberId: Long,
    ): ApiResponse<MemberProfileResponse> {
        requireProfileViewer(authentication)
        return ApiResponse.of(memberService.getProfile(memberId, authentication.principal as Long))
    }

    @Operation(
        summary = "학생 이름 검색",
        description = """
            이름으로 재학 중인(ACTIVE) 학생을 검색한다(name 필수). 교사·개발자 계정과 ACTIVE가 아닌
            상태의 회원은 검색 결과에서 제외된다. academicStatus·cohort·department·majorId·
            techStackId로 추가 필터링할 수 있다. 목록 기본값 page=0, size=20이며 최대 size=100.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "검색 성공(결과 없으면 빈 목록)"),
        SwaggerApiResponse(responseCode = "400", description = "name이 없거나 빈 값 (NAME_REQUIRED)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping
    fun searchMembers(
        authentication: Authentication,
        @Parameter(description = "검색할 학생 이름(부분 일치, 필수)", example = "홍길동")
        @RequestParam(required = false)
        name: String?,
        @Parameter(description = "학적 상태 필터")
        @RequestParam(required = false)
        academicStatus: AcademicStatus?,
        @Parameter(description = "기수 필터", example = "3")
        @RequestParam(required = false)
        cohort: Int?,
        @Parameter(description = "학과 필터")
        @RequestParam(required = false)
        department: DepartmentType?,
        @Parameter(description = "전공 ID 필터(GET /api/v1/metadata/majors에서 조회)", example = "1")
        @RequestParam(required = false)
        majorId: Long?,
        @Parameter(description = "기술 스택 ID 필터(GET /api/v1/metadata/tech-stacks에서 조회)", example = "10")
        @RequestParam(required = false)
        techStackId: Long?,
        @Parameter(description = "Pagination(page: 0부터 시작, size: 기본 20, 최대 100)")
        pageable: Pageable,
    ): ApiResponse<MemberSearchResponse> =
        ApiResponse.of(
            memberSearchService.search(
                authentication.principal as Long,
                name,
                academicStatus,
                cohort,
                department,
                majorId,
                techStackId,
                pageable,
            ),
        )

    /**
     * Access Token의 roles Claim(JwtAuthenticationFilter가 ROLE_ 접두어를 붙여 채운 authorities)을
     * 검사한다(Issue #50 후속, 요청자 Role 검증). 학생뿐 아니라 교사·개발자도 학생 관리·상담
     * 목적으로 조회한다(Issue #114, #89 결정).
     *
     * **Role이 하나도 없는 요청을 막는 것이 이 검사의 실질적인 역할이다.** 승인 대기(PENDING)
     * 교직원은 Role을 부여받지 못한 채 로그인할 수 있고(`OAuthMemberPortImpl.autoGrantRoleFor`),
     * `SecurityConfig`는 이 경로에 인증만 요구하므로 여기까지 도달한다. 이 검사가 없으면 승인
     * 절차를 거치지 않은 계정이 학생 프로필을 조회한다.
     */
    private fun requireProfileViewer(authentication: Authentication) {
        val allowed = authentication.authorities.any { it.authority in PROFILE_VIEWER_AUTHORITIES }
        if (!allowed) throw ProfileViewForbiddenException()
    }

    private companion object {
        /**
         * 현재 [RoleType]이 3개뿐이라 "Role이 하나라도 있으면 허용"과 결과가 같다. 그래도 명시적인
         * Allowlist로 두어, 새 Role이 생겼을 때 여기에 추가하기 전까지는 막히도록 한다.
         */
        val PROFILE_VIEWER_AUTHORITIES =
            setOf(
                "ROLE_${RoleType.STUDENT}",
                "ROLE_${RoleType.TEACHER}",
                "ROLE_${RoleType.DEVELOPER}",
            )
    }
}
