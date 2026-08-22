package team.inreok.getiserver.domain.auth.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.auth.dto.AuthorizeResponse
import team.inreok.getiserver.domain.auth.dto.OAuthLoginResponse
import team.inreok.getiserver.domain.auth.service.AuthLoginService
import team.inreok.getiserver.domain.auth.service.OAuthClientType
import team.inreok.getiserver.domain.auth.service.OAuthLoginIntent
import team.inreok.getiserver.domain.auth.service.OAuthLoginService
import team.inreok.getiserver.domain.auth.service.OAuthWebRedirectUriResolver
import team.inreok.getiserver.domain.auth.service.RefreshTokenCookieFactory
import team.inreok.getiserver.domain.auth.service.impl.OAuthClientTypeStore
import team.inreok.getiserver.domain.auth.service.impl.OAuthLoginIntentStore
import team.inreok.getiserver.global.error.BusinessException
import team.inreok.getiserver.global.web.ApiResponse
import java.net.URI
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Auth - OAuth 로그인", description = "교직원(Google), 학생(DG) OAuth 로그인 시작과 콜백을 처리한다. 인증 없이 접근 가능하다.")
@RestController
@RequestMapping("/api/v1/auth/{provider}")
class OAuthController(
    private val oAuthLoginService: OAuthLoginService,
    private val authLoginService: AuthLoginService,
    private val refreshTokenCookieFactory: RefreshTokenCookieFactory,
    private val oAuthClientTypeStore: OAuthClientTypeStore,
    private val oAuthLoginIntentStore: OAuthLoginIntentStore,
    private val oAuthWebRedirectUriResolver: OAuthWebRedirectUriResolver,
) {
    private val log = LoggerFactory.getLogger(OAuthController::class.java)

    // Frontend(Web/App)가 이 URL로 직접 이동(Redirect)해 Google 로그인 화면을 띄운다. Web/App이
    // 동일한 방식으로 소비할 수 있도록 302 Redirect 대신 JSON으로 URL을 반환한다.
    @Operation(
        summary = "OAuth 로그인 URL 발급",
        description = """
            provider(google, dg)에 해당하는 OAuth 인가 URL을 발급한다. Client는 응답의 authorizationUrl로
            사용자를 이동시켜 로그인 화면을 띄운다. Google은 PKCE(code_verifier), DG는 state 기반으로
            CSRF를 방지하며, 두 값 모두 서버가 Redis에 짧은 TTL로 보관하고 응답에는 state만 포함한다.

            clientType=WEB을 지정하면 이 state로 들어오는 `/callback`이 JSON 대신 Web Client 복귀용
            302 Redirect로 응답한다(Issue #162, DECISION_REQUIRED -- 이 Parameter는 확정된 외부 계약이
            아니라 이번 Issue에서 도입한 최소 설계다). 지정하지 않으면(App 등 기존 호출자) `/callback`이
            기존과 동일하게 JSON을 반환한다. 인증 없이 접근 가능하다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "발급 성공"),
        SwaggerApiResponse(
            responseCode = "400",
            description = "지원하지 않는 provider이거나 Google/DG Client 설정이 비어 있음 (UNSUPPORTED_OAUTH_PROVIDER)",
        ),
        SwaggerApiResponse(
            responseCode = "500",
            description = "서버 내부 오류. clientType=WEB인데 Web Redirect URL이 설정되지 않음(OAUTH_WEB_REDIRECT_NOT_CONFIGURED) 포함",
        ),
    )
    @GetMapping("/authorize")
    fun authorize(
        @Parameter(description = "로그인 방식", example = "google", schema = Schema(allowableValues = ["google", "dg"]))
        @PathVariable
        provider: String,
        @Parameter(
            description = "WEB이면 /callback이 Frontend로 302 Redirect한다. 지정하지 않으면(App 등) 기존 JSON 응답을 유지한다.",
        )
        @RequestParam(required = false)
        clientType: OAuthClientType?,
        @Parameter(
            description =
                "REAPPLY이면 거절된(REJECTED) 교직원의 재신청으로 처리한다 -- /callback이 회원을 승인 대기" +
                    "(PENDING)로 되돌린다. 지정하지 않으면 일반 로그인이다(Issue #229).",
        )
        @RequestParam(required = false)
        intent: OAuthLoginIntent?,
    ): ApiResponse<AuthorizeResponse> {
        // Provider 로그인 왕복을 시작하기 전에 Web Redirect 설정 누락을 미리 걸러낸다(Fail-Fast) --
        // 사용자가 로그인을 끝까지 완료한 뒤에야 Redirect가 깨진 것을 알게 되는 상황을 막는다.
        if (clientType == OAuthClientType.WEB) oAuthWebRedirectUriResolver.requireConfigured()
        val authorization = oAuthLoginService.getAuthorizationUrl(provider)
        if (clientType != null) oAuthClientTypeStore.save(authorization.state, clientType)
        if (intent != null) oAuthLoginIntentStore.save(authorization.state, intent)
        return ApiResponse.of(AuthorizeResponse(authorization.authorizationUrl, authorization.state))
    }

    // Provider가 사용자 동의 후 이 URL로 code/state를 붙여 Redirect한다. code/state를 교환해 회원을
    // 조회/생성하고 GETI 자체 Access/Refresh Token을 발급한다. authorize에서 clientType=WEB을 밝힌
    // 요청이면 JSON 대신 Frontend Callback URL로 302 Redirect한다(Issue #162) -- Access/Refresh
    // Token은 URL에 담기지 않는다. Frontend는 이미 설정된 Refresh Token Cookie로
    // POST /api/v1/auth/token/refresh를 호출해 Access Token을 얻고, 필요하면
    // GET /api/v1/me/profile로 회원 상태(isNewMember/status에 해당하는 정보)를 조회한다.
    @Operation(
        summary = "OAuth 콜백 처리 및 로그인 완료",
        description = """
            OAuth Provider가 사용자 동의 후 이 Endpoint로 code/state를 Redirect한다. 서버는 code/state를
            교환해 사용자 식별값을 얻고, 그 값으로 GETI 회원을 조회하거나(없으면) status=PENDING으로 신규
            생성한 뒤 GETI 자체 Access/Refresh Token을 발급하고 Refresh Token을 HttpOnly Cookie로 설정한다.

            authorize에서 clientType=WEB을 지정한 요청이면 Token/회원 정보를 Body에 담지 않고 대신
            302로 Frontend Callback URL로 Redirect한다(Body 없음, Token은 URL에도 담기지 않는다).
            Frontend는 Cookie 기반 `POST /api/v1/auth/token/refresh`로 Access Token을 얻고,
            `GET /api/v1/me/profile`로 회원 상태(최초 로그인·승인 대기 여부 판단)를 조회한다. 로그인
            자체가 실패해도(state 무효, Provider 실패 등) 같은 Callback URL로 `error={ErrorCode}` Query
            Parameter만 붙여 Redirect한다(Provider 원본 오류, Stack Trace, code, state는 포함하지 않는다).

            clientType을 지정하지 않은 요청(App 등)은 기존과 동일하게 200과 함께 Token·회원 정보를
            그대로 반환한다(Breaking Change 없음). 인증 없이 접근 가능하다.

            거절된(REJECTED) 교직원이 재신청 없이 로그인하면 403(MEMBER_SIGNUP_REJECTED)으로 막고
            응답 message에 거절 사유를 담아 `/staff/signup`이 표시할 수 있게 한다. authorize에서
            intent=REAPPLY로 시작한 재신청이면 회원을 승인 대기(PENDING)로 되돌리고 정상 로그인시킨다
            (Issue #229).
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "로그인 성공(clientType 미지정, Token 및 회원 정보 반환)",
        ),
        SwaggerApiResponse(
            responseCode = "302",
            description = "로그인 성공/실패(clientType=WEB), Frontend Callback URL로 Redirect(Body 없음)",
        ),
        SwaggerApiResponse(
            responseCode = "400",
            description = "state가 만료되었거나 유효하지 않음(OAUTH_STATE_INVALID, clientType 미지정 시에만 이 Status로 응답)",
        ),
        SwaggerApiResponse(
            responseCode = "401",
            description = "Provider Token 교환·UserInfo 조회 실패(OAUTH_LOGIN_FAILED, clientType 미지정 시에만 이 Status로 응답)",
        ),
        SwaggerApiResponse(
            responseCode = "403",
            description =
                "가입이 거절된 교직원이 재신청 없이 로그인함 -- 응답 message에 거절 사유 포함" +
                    "(MEMBER_SIGNUP_REJECTED), 또는 정지/탈퇴 등 로그인 불가 상태" +
                    "(MEMBER_LOGIN_NOT_ALLOWED). clientType 미지정 시에만 이 Status로 응답한다.",
        ),
        SwaggerApiResponse(
            responseCode = "409",
            description = "같은 이메일이 다른 방식으로 이미 가입됨(OAUTH_EMAIL_ALREADY_REGISTERED, clientType 미지정 시에만 이 Status로 응답)",
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
        response: HttpServletResponse,
    ): ResponseEntity<ApiResponse<OAuthLoginResponse>> {
        // state 하나당 1회만 소비한다(OAuthClientTypeStore.consume 참고). authorize에서
        // clientType을 저장하지 않았으면(App 등) 항상 null이라 기존 JSON 경로로 그대로 떨어진다.
        val clientType = oAuthClientTypeStore.consume(state)
        val reapply = oAuthLoginIntentStore.consume(state) == OAuthLoginIntent.REAPPLY
        return try {
            val login = authLoginService.loginWithOAuth(provider, code, state, reapply)
            // Web Client용 HttpOnly Cookie로도 Refresh Token을 내려준다(Body에도 함께 담김, Issue
            // #105). clientType=WEB이어도 이후 Frontend가 Cookie 기반 Refresh로 Access Token을
            // 얻어야 하므로 Cookie 자체는 항상 설정한다.
            response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.create(login.refreshToken).toString())
            if (clientType == OAuthClientType.WEB) {
                redirectTo(oAuthWebRedirectUriResolver.successUri())
            } else {
                ResponseEntity.ok(ApiResponse.of(login))
            }
        } catch (ex: BusinessException) {
            if (clientType == OAuthClientType.WEB) {
                log.warn("OAuth 로그인 실패, Web Client로 Redirect함(provider={}, errorCode={})", provider, ex.errorCode.code)
                redirectTo(oAuthWebRedirectUriResolver.failureUri(ex.errorCode.code))
            } else {
                throw ex
            }
        }
    }

    private fun redirectTo(uri: URI): ResponseEntity<ApiResponse<OAuthLoginResponse>> =
        ResponseEntity.status(HttpStatus.FOUND).location(uri).body(null)
}
