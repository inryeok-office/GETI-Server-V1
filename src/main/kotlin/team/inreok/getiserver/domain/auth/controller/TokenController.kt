package team.inreok.getiserver.domain.auth.controller

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.auth.dto.TokenRefreshRequest
import team.inreok.getiserver.domain.auth.dto.TokenRefreshResponse
import team.inreok.getiserver.domain.auth.service.TokenService
import team.inreok.getiserver.global.web.ApiResponse

@RestController
@RequestMapping("/api/v1/auth/token")
class TokenController(
    private val tokenService: TokenService,
) {
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
