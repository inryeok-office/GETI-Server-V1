package team.inreok.getiserver.domain.auth.controller

import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.auth.dto.OAuthLoginResponse
import team.inreok.getiserver.domain.auth.exception.OAuthLoginFailedException
import team.inreok.getiserver.domain.auth.exception.OAuthStateInvalidException
import team.inreok.getiserver.domain.auth.exception.OAuthWebRedirectNotConfiguredException
import team.inreok.getiserver.domain.auth.exception.UnsupportedOAuthProviderException
import team.inreok.getiserver.domain.auth.service.AuthLoginService
import team.inreok.getiserver.domain.auth.service.OAuthAuthorization
import team.inreok.getiserver.domain.auth.service.OAuthClientType
import team.inreok.getiserver.domain.auth.service.OAuthLoginIntent
import team.inreok.getiserver.domain.auth.service.OAuthLoginService
import team.inreok.getiserver.domain.auth.service.OAuthWebRedirectUriResolver
import team.inreok.getiserver.domain.auth.service.RefreshTokenCookieFactory
import team.inreok.getiserver.domain.auth.service.impl.OAuthClientTypeStore
import team.inreok.getiserver.domain.auth.service.impl.OAuthLoginIntentStore
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.exception.MemberLoginNotAllowedException
import team.inreok.getiserver.domain.member.exception.OAuthEmailAlreadyRegisteredException
import team.inreok.getiserver.domain.member.exception.StaffSignupRejectedException
import java.net.URI

@WebMvcTest(controllers = [OAuthController::class])
class OAuthControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        // OAuthController는 /authorize용 OAuthLoginService와 /callback용 AuthLoginService를 모두
        // 주입받으므로 두 Bean 모두 Mock으로 제공한다.
        @MockitoBean
        private lateinit var oAuthLoginService: OAuthLoginService

        @MockitoBean
        private lateinit var authLoginService: AuthLoginService

        @MockitoBean
        private lateinit var refreshTokenCookieFactory: RefreshTokenCookieFactory

        @MockitoBean
        private lateinit var oAuthClientTypeStore: OAuthClientTypeStore

        @MockitoBean
        private lateinit var oAuthLoginIntentStore: OAuthLoginIntentStore

        @MockitoBean
        private lateinit var oAuthWebRedirectUriResolver: OAuthWebRedirectUriResolver

        @Test
        fun `authorize는 200과 함께 인가 URL과 state를 반환한다`() {
            given(oAuthLoginService.getAuthorizationUrl("google"))
                .willReturn(
                    OAuthAuthorization(
                        authorizationUrl = "https://accounts.google.com/o/oauth2/v2/auth?client_id=x&state=state-1",
                        state = "state-1",
                    ),
                )

            mockMvc
                .perform(get("/api/v1/auth/google/authorize"))
                .andExpect(status().isOk)
                .andExpect(
                    jsonPath(
                        "$.data.authorizationUrl",
                    ).value("https://accounts.google.com/o/oauth2/v2/auth?client_id=x&state=state-1"),
                ).andExpect(jsonPath("$.data.state").value("state-1"))
        }

        @Test
        fun `authorize에서 지원하지 않는 Provider이면 400을 반환한다`() {
            willThrow(UnsupportedOAuthProviderException("kakao"))
                .given(oAuthLoginService)
                .getAuthorizationUrl("kakao")

            mockMvc
                .perform(get("/api/v1/auth/kakao/authorize"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_OAUTH_PROVIDER"))
        }

        @Test
        fun `콜백이 성공하면 200과 함께 Token, 회원 정보, Refresh Token Cookie를 반환한다`() {
            given(authLoginService.loginWithOAuth("google", "auth-code", "state-value"))
                .willReturn(
                    OAuthLoginResponse(
                        accessToken = "access-token",
                        refreshToken = "refresh-token",
                        accessTokenExpiresInSeconds = 1800,
                        memberId = 1L,
                        roles = listOf("TEACHER"),
                        status = "ACTIVE",
                        isNewMember = false,
                    ),
                )
            given(refreshTokenCookieFactory.create("refresh-token"))
                .willReturn(
                    ResponseCookie
                        .from("refreshToken", "refresh-token")
                        .httpOnly(true)
                        .path("/api/v1/auth")
                        .build(),
                )

            mockMvc
                .perform(get("/api/v1/auth/google/callback").param("code", "auth-code").param("state", "state-value"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.data.accessTokenExpiresInSeconds").value(1800))
                .andExpect(jsonPath("$.data.memberId").value(1))
                .andExpect(jsonPath("$.data.roles[0]").value("TEACHER"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.isNewMember").value(false))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=refresh-token")))
        }

        @Test
        fun `지원하지 않는 Provider이면 400을 반환한다`() {
            willThrow(UnsupportedOAuthProviderException("kakao"))
                .given(authLoginService)
                .loginWithOAuth("kakao", "auth-code", "state-value")

            mockMvc
                .perform(get("/api/v1/auth/kakao/callback").param("code", "auth-code").param("state", "state-value"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_OAUTH_PROVIDER"))
        }

        @Test
        fun `state가 유효하지 않으면 400을 반환한다`() {
            willThrow(OAuthStateInvalidException())
                .given(authLoginService)
                .loginWithOAuth("google", "auth-code", "invalid-state")

            mockMvc
                .perform(get("/api/v1/auth/google/callback").param("code", "auth-code").param("state", "invalid-state"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("OAUTH_STATE_INVALID"))
        }

        @Test
        fun `Token 교환이 실패하면 401을 반환한다`() {
            willThrow(OAuthLoginFailedException("Google 로그인에 실패했습니다."))
                .given(authLoginService)
                .loginWithOAuth("google", "invalid-code", "state-value")

            mockMvc
                .perform(
                    get("/api/v1/auth/google/callback").param("code", "invalid-code").param("state", "state-value"),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("OAUTH_LOGIN_FAILED"))
                .andExpect(jsonPath("$.error.message").value("Google 로그인에 실패했습니다."))
        }

        @Test
        fun `이미 다른 방식으로 가입된 이메일이면 409를 반환한다`() {
            willThrow(OAuthEmailAlreadyRegisteredException("teacher@example.com"))
                .given(authLoginService)
                .loginWithOAuth("google", "auth-code", "state-value")

            mockMvc
                .perform(get("/api/v1/auth/google/callback").param("code", "auth-code").param("state", "state-value"))
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.error.code").value("OAUTH_EMAIL_ALREADY_REGISTERED"))
        }

        @Test
        fun `로그인이 허용되지 않는 회원 상태이면 403을 반환한다`() {
            willThrow(MemberLoginNotAllowedException(MemberStatus.SUSPENDED))
                .given(authLoginService)
                .loginWithOAuth("google", "auth-code", "state-value")

            mockMvc
                .perform(get("/api/v1/auth/google/callback").param("code", "auth-code").param("state", "state-value"))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("MEMBER_LOGIN_NOT_ALLOWED"))
        }

        @Test
        fun `가입이 거절된 교직원이 재신청 없이 로그인하면 403과 거절 사유를 반환한다`() {
            willThrow(StaffSignupRejectedException("증빙 서류가 확인되지 않았습니다."))
                .given(authLoginService)
                .loginWithOAuth("google", "auth-code", "state-value", false)

            mockMvc
                .perform(get("/api/v1/auth/google/callback").param("code", "auth-code").param("state", "state-value"))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("MEMBER_SIGNUP_REJECTED"))
                .andExpect(jsonPath("$.error.message").value("증빙 서류가 확인되지 않았습니다."))
        }

        // ---------- 재신청 Flow(Issue #229) ----------

        @Test
        fun `authorize에 intent=REAPPLY를 지정하면 state에 저장한다`() {
            given(oAuthLoginService.getAuthorizationUrl("google"))
                .willReturn(OAuthAuthorization(authorizationUrl = "https://accounts.google.com/x", state = "state-1"))

            mockMvc
                .perform(get("/api/v1/auth/google/authorize").param("intent", "REAPPLY"))
                .andExpect(status().isOk)

            verify(oAuthLoginIntentStore).save("state-1", OAuthLoginIntent.REAPPLY)
        }

        @Test
        fun `콜백이 재신청(intent=REAPPLY) 요청이면 reapply=true로 로그인에 위임한다`() {
            given(oAuthLoginIntentStore.consume("state-value")).willReturn(OAuthLoginIntent.REAPPLY)
            given(authLoginService.loginWithOAuth("google", "auth-code", "state-value", true))
                .willReturn(
                    OAuthLoginResponse(
                        accessToken = "access-token",
                        refreshToken = "refresh-token",
                        accessTokenExpiresInSeconds = 1800,
                        memberId = 1L,
                        roles = emptyList(),
                        status = "PENDING",
                        isNewMember = false,
                    ),
                )
            given(refreshTokenCookieFactory.create("refresh-token"))
                .willReturn(
                    ResponseCookie
                        .from("refreshToken", "refresh-token")
                        .httpOnly(true)
                        .path("/api/v1/auth")
                        .build(),
                )

            mockMvc
                .perform(get("/api/v1/auth/google/callback").param("code", "auth-code").param("state", "state-value"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.status").value("PENDING"))

            verify(authLoginService).loginWithOAuth("google", "auth-code", "state-value", true)
        }

        // ---------- Web Client 복귀 Flow(Issue #162) ----------

        @Test
        fun `authorize에 clientType=WEB을 지정하면 state에 저장하고 Redirect 설정을 미리 검증한다`() {
            given(oAuthLoginService.getAuthorizationUrl("google"))
                .willReturn(
                    OAuthAuthorization(
                        authorizationUrl = "https://accounts.google.com/o/oauth2/v2/auth?client_id=x&state=state-1",
                        state = "state-1",
                    ),
                )

            mockMvc
                .perform(get("/api/v1/auth/google/authorize").param("clientType", "WEB"))
                .andExpect(status().isOk)

            verify(oAuthWebRedirectUriResolver).requireConfigured()
            verify(oAuthClientTypeStore).save("state-1", OAuthClientType.WEB)
        }

        @Test
        fun `authorize에 clientType을 지정하지 않으면 저장하지 않고 기존과 동일하게 응답한다`() {
            given(oAuthLoginService.getAuthorizationUrl("google"))
                .willReturn(OAuthAuthorization(authorizationUrl = "https://accounts.google.com/x", state = "state-1"))

            mockMvc
                .perform(get("/api/v1/auth/google/authorize"))
                .andExpect(status().isOk)

            verify(oAuthWebRedirectUriResolver, never()).requireConfigured()
            verify(oAuthClientTypeStore, never()).save("state-1", OAuthClientType.WEB)
        }

        @Test
        fun `authorize에 clientType=WEB인데 Redirect URL이 설정되지 않으면 500을 반환한다`() {
            willThrow(OAuthWebRedirectNotConfiguredException())
                .given(oAuthWebRedirectUriResolver)
                .requireConfigured()

            mockMvc
                .perform(get("/api/v1/auth/google/authorize").param("clientType", "WEB"))
                .andExpect(status().isInternalServerError)
                .andExpect(jsonPath("$.error.code").value("OAUTH_WEB_REDIRECT_NOT_CONFIGURED"))
        }

        @Test
        fun `콜백이 clientType=WEB으로 성공하면 Body 없이 302로 Frontend URL로 Redirect하고 Refresh Cookie는 그대로 설정한다`() {
            given(oAuthClientTypeStore.consume("state-value")).willReturn(OAuthClientType.WEB)
            given(authLoginService.loginWithOAuth("google", "auth-code", "state-value"))
                .willReturn(
                    OAuthLoginResponse(
                        accessToken = "access-token",
                        refreshToken = "refresh-token",
                        accessTokenExpiresInSeconds = 1800,
                        memberId = 1L,
                        roles = listOf("STUDENT"),
                        status = "ACTIVE",
                        isNewMember = false,
                    ),
                )
            given(refreshTokenCookieFactory.create("refresh-token"))
                .willReturn(
                    ResponseCookie
                        .from("refreshToken", "refresh-token")
                        .httpOnly(true)
                        .path("/api/v1/auth")
                        .build(),
                )
            given(
                oAuthWebRedirectUriResolver.successUri(),
            ).willReturn(URI.create("https://web.example.com/auth/callback"))

            mockMvc
                .perform(get("/api/v1/auth/google/callback").param("code", "auth-code").param("state", "state-value"))
                .andExpect(status().isFound)
                .andExpect(header().string(HttpHeaders.LOCATION, "https://web.example.com/auth/callback"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=refresh-token")))
                .andExpect(jsonPath("$").doesNotExist())
        }

        @Test
        fun `콜백이 clientType=WEB인데 실패하면 error Query Parameter만 붙여 302로 Redirect한다`() {
            given(oAuthClientTypeStore.consume("invalid-state")).willReturn(OAuthClientType.WEB)
            willThrow(OAuthStateInvalidException())
                .given(authLoginService)
                .loginWithOAuth("google", "auth-code", "invalid-state")
            given(oAuthWebRedirectUriResolver.failureUri("OAUTH_STATE_INVALID"))
                .willReturn(URI.create("https://web.example.com/auth/callback?error=OAUTH_STATE_INVALID"))

            mockMvc
                .perform(get("/api/v1/auth/google/callback").param("code", "auth-code").param("state", "invalid-state"))
                .andExpect(status().isFound)
                .andExpect(
                    header().string(
                        HttpHeaders.LOCATION,
                        "https://web.example.com/auth/callback?error=OAUTH_STATE_INVALID",
                    ),
                )
        }

        @Test
        fun `콜백이 clientType을 지정하지 않았으면 실패해도 기존처럼 JSON 오류를 그대로 반환한다`() {
            given(oAuthClientTypeStore.consume("invalid-state")).willReturn(null)
            willThrow(OAuthStateInvalidException())
                .given(authLoginService)
                .loginWithOAuth("google", "auth-code", "invalid-state")

            mockMvc
                .perform(get("/api/v1/auth/google/callback").param("code", "auth-code").param("state", "invalid-state"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("OAUTH_STATE_INVALID"))
        }
    }
