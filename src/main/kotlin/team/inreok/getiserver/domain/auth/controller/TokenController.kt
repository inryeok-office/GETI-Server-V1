package team.inreok.getiserver.domain.auth.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.auth.dto.TokenRefreshRequest
import team.inreok.getiserver.domain.auth.dto.TokenRefreshResponse
import team.inreok.getiserver.domain.auth.service.TokenService
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(
    name = "Auth - 토큰",
    description = "Refresh Token으로 Access/Refresh Token을 재발급한다. Access Token 없이 Refresh Token만으로 호출한다.",
)
@RestController
@RequestMapping("/api/v1/auth/token")
class TokenController(
    private val tokenService: TokenService,
) {
    @Operation(
        summary = "Access/Refresh Token 재발급",
        description = """
            유효한 Refresh Token을 즉시 폐기(1회용 Rotation)하고 새 Access/Refresh Token 쌍을 발급한다.
            Access Token은 아직 Member 도메인과 연동되지 않아 역할(roles) Claim이 항상 빈 목록이다.
            Access Token(Authorization Header) 없이 Body의 refreshToken만으로 호출한다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "재발급 성공"),
        SwaggerApiResponse(responseCode = "400", description = "요청 값 검증 실패, refreshToken 누락 (VALIDATION_FAILED)"),
        SwaggerApiResponse(
            responseCode = "401",
            description = "Refresh Token이 존재하지 않거나 만료·폐기됨 (INVALID_REFRESH_TOKEN)",
        ),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PostMapping("/refresh")
    fun refresh(
        @Valid @RequestBody request: TokenRefreshRequest,
    ): ApiResponse<TokenRefreshResponse> {
        val issued = tokenService.refresh(request.refreshToken, request.deviceIdentifier)
        return ApiResponse.of(
            TokenRefreshResponse(
                accessToken = issued.accessToken,
                refreshToken = issued.refreshToken,
                accessTokenExpiresInSeconds = issued.accessTokenExpiresInSeconds,
            ),
        )
    }
}
