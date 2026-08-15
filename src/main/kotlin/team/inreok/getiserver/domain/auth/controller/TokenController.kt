package team.inreok.getiserver.domain.auth.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.auth.dto.TokenRefreshRequest
import team.inreok.getiserver.domain.auth.dto.TokenRefreshResponse
import team.inreok.getiserver.domain.auth.exception.RefreshTokenRequiredException
import team.inreok.getiserver.domain.auth.service.RefreshTokenCookieFactory
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
    private val refreshTokenCookieFactory: RefreshTokenCookieFactory,
) {
    @Operation(
        summary = "Access/Refresh Token 재발급",
        description = """
            유효한 Refresh Token을 즉시 폐기(1회용 Rotation)하고 새 Access/Refresh Token 쌍을 발급한다.
            Refresh Token은 Cookie(refreshToken) > X-Refresh-Token Header > Body(refreshToken) 순으로 읽는다.
            새 Access Token의 역할(roles) Claim은 해당 회원의 현재 Member Role을 다시 조회해 반영한다.
            새 Refresh Token은 HttpOnly Cookie(Set-Cookie)로도 내려주며, 응답 Body에도 함께 담는다.
            Access Token(Authorization Header) 없이 호출한다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "재발급 성공(Set-Cookie: refreshToken 포함)"),
        SwaggerApiResponse(
            responseCode = "400",
            description = "Refresh Token이 Cookie/Header/Body 어디에도 없음 (REFRESH_TOKEN_REQUIRED)",
        ),
        SwaggerApiResponse(
            responseCode = "401",
            description = "Refresh Token이 존재하지 않거나 만료·폐기됨 (INVALID_REFRESH_TOKEN)",
        ),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PostMapping("/refresh")
    fun refresh(
        @RequestBody(required = false) request: TokenRefreshRequest?,
        @Parameter(description = "Refresh Token(Web용 HttpOnly Cookie). Cookie > X-Refresh-Token Header > Body 순으로 읽는다.")
        @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false)
        cookieToken: String?,
        @Parameter(description = "Refresh Token(App용 Header). Cookie가 없을 때 사용한다.")
        @RequestHeader(name = RefreshTokenCookieFactory.HEADER_NAME, required = false)
        headerToken: String?,
        response: HttpServletResponse,
    ): ApiResponse<TokenRefreshResponse> {
        val refreshToken =
            refreshTokenCookieFactory.resolve(cookieToken, headerToken, request?.refreshToken)
                ?: throw RefreshTokenRequiredException()
        val issued = tokenService.refresh(refreshToken, request?.deviceIdentifier)
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.create(issued.refreshToken).toString())
        return ApiResponse.of(
            TokenRefreshResponse(
                accessToken = issued.accessToken,
                refreshToken = issued.refreshToken,
                accessTokenExpiresInSeconds = issued.accessTokenExpiresInSeconds,
            ),
        )
    }
}
