package team.inreok.getiserver.domain.auth.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.auth.dto.AuthorizeResponse
import team.inreok.getiserver.domain.auth.dto.OAuthCallbackResponse
import team.inreok.getiserver.domain.auth.service.OAuthLoginService
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Auth - OAuth 로그인", description = "교직원(Google), 학생(DG) OAuth 로그인 시작과 콜백을 처리한다. 인증 없이 접근 가능하다.")
@RestController
@RequestMapping("/api/v1/auth/{provider}")
class OAuthController(
    private val oAuthLoginService: OAuthLoginService,
) {
    // Frontend(Web/App)가 이 URL로 직접 이동(Redirect)해 Google 로그인 화면을 띄운다. Web/App이
    // 동일한 방식으로 소비할 수 있도록 302 Redirect 대신 JSON으로 URL을 반환한다.
    @Operation(
        summary = "OAuth 로그인 URL 발급",
        description = """
            provider(google, dg)에 해당하는 OAuth 인가 URL을 발급한다. Client는 응답의 authorizationUrl로
            사용자를 이동시켜 로그인 화면을 띄운다. Google은 PKCE(code_verifier), DG는 state 기반으로
            CSRF를 방지하며, 두 값 모두 서버가 Redis에 짧은 TTL로 보관하고 응답에는 state만 포함한다.
            인증 없이 접근 가능하다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "발급 성공"),
        SwaggerApiResponse(
            responseCode = "400",
            description = "지원하지 않는 provider이거나 Google/DG Client 설정이 비어 있음 (UNSUPPORTED_OAUTH_PROVIDER)",
        ),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping("/authorize")
    fun authorize(
        @Parameter(description = "로그인 방식", example = "google", schema = Schema(allowableValues = ["google", "dg"]))
        @PathVariable
        provider: String,
    ): ApiResponse<AuthorizeResponse> {
        val authorization = oAuthLoginService.getAuthorizationUrl(provider)
        return ApiResponse.of(AuthorizeResponse(authorization.authorizationUrl, authorization.state))
    }

    // Google이 사용자 동의 후 이 URL로 code/state를 붙여 Redirect한다. code/state 교환까지만
    // 수행하고 Provider의 사용자 식별값(subject/email)을 그대로 반환한다 — 이 식별값으로 회원을
    // 조회/생성하고 GETI 자체 Token을 발급하는 것은 Member 도메인 연동이 필요해 후속 PR에서
    // 이어서 구현한다(OAuthLoginService.exchangeCode 참고).
    @Operation(
        summary = "OAuth 콜백 처리",
        description = """
            OAuth Provider가 사용자 동의 후 이 Endpoint로 code/state를 Redirect한다. code/state 교환까지만
            수행하고 Provider의 사용자 식별값(subject, email)을 그대로 반환한다. 이 식별값으로 GETI 회원을
            조회·생성하고 자체 Access/Refresh Token을 발급하는 절차는 아직 연동되지 않았다(후속 작업).
            인증 없이 접근 가능하다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "Provider 사용자 식별값 반환 성공"),
        SwaggerApiResponse(
            responseCode = "400",
            description =
                "state가 만료되었거나 유효하지 않음(OAUTH_STATE_INVALID), " +
                    "Provider Token 교환·UserInfo 조회 실패(OAUTH_LOGIN_FAILED)",
        ),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping("/callback")
    fun callback(
        @Parameter(description = "로그인 방식", example = "google", schema = Schema(allowableValues = ["google", "dg"]))
        @PathVariable
        provider: String,
        @Parameter(
            description = "Provider가 발급한 Authorization Code",
            example = "4/0AY0e-g7...",
            `in` = ParameterIn.QUERY,
        )
        @RequestParam
        code: String,
        @Parameter(
            description = "authorize 요청 때 발급받은 state(CSRF 방지용)",
            example = "b64f2c1a-9e3d-4b7a-8f21-1a2b3c4d5e6f",
            `in` = ParameterIn.QUERY,
        )
        @RequestParam
        state: String,
    ): ApiResponse<OAuthCallbackResponse> {
        val userInfo = oAuthLoginService.exchangeCode(provider, code, state)
        return ApiResponse.of(OAuthCallbackResponse(subject = userInfo.subject, email = userInfo.email))
    }
}
