package team.inreok.getiserver.domain.auth.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.auth.dto.LogoutRequest
import team.inreok.getiserver.domain.auth.dto.SessionResponse
import team.inreok.getiserver.domain.auth.service.TokenService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Auth - 세션", description = "로그인된 사용자의 세션 조회와 로그아웃을 처리한다. 필요 권한: 학생, 교사, 개발자.")
@RestController
@RequestMapping("/api/v1/auth")
class SessionController(
    private val tokenService: TokenService,
) {
    // SecurityConfig가 이 경로를 인증 필수로 지정하므로, 여기 도달했다는 것은 이미 유효한
    // Access Token(JwtAuthenticationFilter)이 SecurityContext에 인증 정보를 채웠다는 뜻이다.
    @Operation(
        summary = "현재 로그인 사용자 확인",
        description = "Access Token(Claim)에서 추출한 현재 로그인한 사용자의 memberId와 역할(roles) 목록을 반환한다.",
    )
    @SecurityRequirement(name = BEARER_AUTH_SCHEME)
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping("/session")
    fun getSession(authentication: Authentication): ApiResponse<SessionResponse> {
        val memberId = authentication.principal as Long
        val roles = authentication.authorities.map { it.authority.orEmpty().removePrefix("ROLE_") }
        return ApiResponse.of(SessionResponse(memberId = memberId, roles = roles))
    }

    // 인증된 호출자(memberId)를 함께 넘겨, 다른 사용자의 Refresh Token을 강제 폐기하지 못하도록
    // Service 계층에서 소유권을 검증한다(코드 리뷰 Blocker 반영).
    @Operation(
        summary = "현재 기기 로그아웃",
        description = """
            요청 Body의 Refresh Token을 폐기해 재발급을 막는다. Access Token의 소유자와 Refresh Token의
            소유자가 다르면 거부한다(다른 사용자의 세션을 강제 로그아웃시키는 것을 방지). 성공 시 Body 없이
            204를 반환한다.
        """,
    )
    @SecurityRequirement(name = BEARER_AUTH_SCHEME)
    @ApiResponses(
        SwaggerApiResponse(responseCode = "204", description = "로그아웃 성공(Body 없음)"),
        SwaggerApiResponse(
            responseCode = "400",
            description = "요청 값 검증 실패, refreshToken 누락 (VALIDATION_FAILED)",
        ),
        SwaggerApiResponse(
            responseCode = "401",
            description =
                "Access Token이 유효하지 않음(UNAUTHORIZED), 또는 Refresh Token이 유효하지 않거나 " +
                    "다른 사용자 소유임(INVALID_REFRESH_TOKEN)",
        ),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @DeleteMapping("/logout")
    fun logout(
        authentication: Authentication,
        @Valid @RequestBody request: LogoutRequest,
    ): ResponseEntity<Void> {
        val memberId = authentication.principal as Long
        tokenService.logout(request.refreshToken, memberId)
        return ResponseEntity.noContent().build()
    }
}
