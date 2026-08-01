package team.inreok.getiserver.domain.auth.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.auth.dto.AuthorizeResponse
import team.inreok.getiserver.domain.auth.dto.OAuthCallbackResponse
import team.inreok.getiserver.domain.auth.service.OAuthLoginService
import team.inreok.getiserver.global.web.ApiResponse

@RestController
@RequestMapping("/api/v1/auth/{provider}")
class OAuthController(
    private val oAuthLoginService: OAuthLoginService,
) {
    // Frontend(Web/App)가 이 URL로 직접 이동(Redirect)해 Google 로그인 화면을 띄운다. Web/App이
    // 동일한 방식으로 소비할 수 있도록 302 Redirect 대신 JSON으로 URL을 반환한다.
    @GetMapping("/authorize")
    fun authorize(
        @PathVariable provider: String,
    ): ApiResponse<AuthorizeResponse> {
        val authorization = oAuthLoginService.getAuthorizationUrl(provider)
        return ApiResponse.of(AuthorizeResponse(authorization.authorizationUrl, authorization.state))
    }

    // Google이 사용자 동의 후 이 URL로 code/state를 붙여 Redirect한다. code/state 교환까지만
    // 수행하고 Provider의 사용자 식별값(subject/email)을 그대로 반환한다 — 이 식별값으로 회원을
    // 조회/생성하고 GETI 자체 Token을 발급하는 것은 Member 도메인 연동이 필요해 후속 PR에서
    // 이어서 구현한다(OAuthLoginService.exchangeCode 참고).
    @GetMapping("/callback")
    fun callback(
        @PathVariable provider: String,
        @RequestParam code: String,
        @RequestParam state: String,
    ): ApiResponse<OAuthCallbackResponse> {
        val userInfo = oAuthLoginService.exchangeCode(provider, code, state)
        return ApiResponse.of(OAuthCallbackResponse(subject = userInfo.subject, email = userInfo.email))
    }
}
