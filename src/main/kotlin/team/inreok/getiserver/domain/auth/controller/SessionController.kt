package team.inreok.getiserver.domain.auth.controller

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
import team.inreok.getiserver.global.web.ApiResponse

@RestController
@RequestMapping("/api/v1/auth")
class SessionController(
    private val tokenService: TokenService,
) {
    // SecurityConfig가 이 경로를 인증 필수로 지정하므로, 여기 도달했다는 것은 이미 유효한
    // Access Token(JwtAuthenticationFilter)이 SecurityContext에 인증 정보를 채웠다는 뜻이다.
    @GetMapping("/session")
    fun getSession(authentication: Authentication): ApiResponse<SessionResponse> {
        val memberId = authentication.principal as Long
        val roles = authentication.authorities.map { it.authority.orEmpty().removePrefix("ROLE_") }
        return ApiResponse.of(SessionResponse(memberId = memberId, roles = roles))
    }

    @DeleteMapping("/logout")
    fun logout(
        @Valid @RequestBody request: LogoutRequest,
    ): ResponseEntity<Void> {
        tokenService.logout(request.refreshToken)
        return ResponseEntity.noContent().build()
    }
}
