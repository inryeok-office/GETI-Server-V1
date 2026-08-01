package team.inreok.getiserver.domain.auth.service.impl

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import team.inreok.getiserver.domain.auth.exception.OAuthLoginFailedException
import team.inreok.getiserver.domain.auth.exception.OAuthStateInvalidException
import team.inreok.getiserver.domain.auth.exception.UnsupportedOAuthProviderException
import team.inreok.getiserver.domain.auth.service.OAuthAuthorization
import team.inreok.getiserver.domain.auth.service.OAuthUserInfo
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Google OAuth2(교직원 로그인) Provider Client다. `spring.security.oauth2.client.registration.*`를
 * 쓰지 않고 원시 설정값을 직접 읽어 인가 URL/Token 교환을 수행하는 이유는 application.yaml의 주석
 * (app.oauth.google.*) 참고 — Client 정보가 비어 있어도(Google 미설정) App이 정상 기동해야 하기
 * 때문이다. Authorization Code Flow + PKCE(S256)를 사용하며, state -> code_verifier 매핑은
 * [OAuthStateStore](Redis)에 짧은 TTL로만 보관한다.
 */
@Component
class GoogleOAuthProviderClient(
    @Value("\${app.oauth.google.client-id:}") private val clientId: String,
    @Value("\${app.oauth.google.client-secret:}") private val clientSecret: String,
    @Value("\${app.oauth.google.redirect-uri:}") private val redirectUri: String,
    @Value("\${app.oauth.google.scope:openid,email,profile}") private val scope: String,
    private val oAuthStateStore: OAuthStateStore,
    restClientBuilder: RestClient.Builder,
) : OAuthProviderClient {
    private val restClient = restClientBuilder.build()

    override val provider: String = "google"

    override fun createAuthorization(): OAuthAuthorization {
        requireConfigured()
        val state = randomUrlSafeToken()
        val codeVerifier = randomUrlSafeToken()
        oAuthStateStore.save(state, codeVerifier)

        val authorizationUrl =
            UriComponentsBuilder
                .fromUriString(AUTHORIZATION_URI)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", scope)
                .queryParam("state", state)
                .queryParam("code_challenge", codeChallenge(codeVerifier))
                .queryParam("code_challenge_method", "S256")
                .queryParam("access_type", "offline")
                .build(true)
                .toUriString()

        return OAuthAuthorization(authorizationUrl = authorizationUrl, state = state)
    }

    override fun exchangeCode(
        code: String,
        state: String,
    ): OAuthUserInfo {
        requireConfigured()
        val codeVerifier = oAuthStateStore.consume(state) ?: throw OAuthStateInvalidException()
        val accessToken = requestAccessToken(code, codeVerifier)
        return fetchUserInfo(accessToken)
    }

    private fun requireConfigured() {
        if (clientId.isBlank() || clientSecret.isBlank() || redirectUri.isBlank()) {
            throw UnsupportedOAuthProviderException(provider)
        }
    }

    private fun requestAccessToken(
        code: String,
        codeVerifier: String,
    ): String {
        val body =
            LinkedMultiValueMap<String, String>().apply {
                add("grant_type", GRANT_TYPE_AUTHORIZATION_CODE)
                add("code", code)
                add("redirect_uri", redirectUri)
                add("client_id", clientId)
                add("client_secret", clientSecret)
                add("code_verifier", codeVerifier)
            }
        val response =
            runCatching {
                restClient
                    .post()
                    .uri(TOKEN_URI)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(object : ParameterizedTypeReference<Map<String, Any?>>() {})
            }.getOrNull() ?: throw OAuthLoginFailedException("Google 로그인에 실패했습니다.")

        return response[ACCESS_TOKEN_FIELD] as? String ?: throw OAuthLoginFailedException("Google 로그인에 실패했습니다.")
    }

    private fun fetchUserInfo(accessToken: String): OAuthUserInfo {
        val response =
            runCatching {
                restClient
                    .get()
                    .uri(USER_INFO_URI)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                    .retrieve()
                    .body(object : ParameterizedTypeReference<Map<String, Any?>>() {})
            }.getOrNull()

        val subject = response?.get(SUBJECT_FIELD) as? String
        val email = response?.get(EMAIL_FIELD) as? String
        // 검증되지 않은 이메일(email_verified != true)은 신뢰하지 않는다. 후속 Member 연동에서 이
        // 이메일로 교직원 여부를 식별하므로 지금부터 검증된 이메일만 통과시킨다(코드 리뷰 P2 반영).
        val emailVerified = response?.get(EMAIL_VERIFIED_FIELD) == true
        if (subject == null || email == null || !emailVerified) {
            throw OAuthLoginFailedException("Google 로그인에 실패했습니다.")
        }
        return OAuthUserInfo(subject = subject, email = email)
    }

    private fun randomUrlSafeToken(): String {
        val bytes = ByteArray(TOKEN_BYTE_LENGTH)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun codeChallenge(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    companion object {
        private const val AUTHORIZATION_URI = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val TOKEN_URI = "https://oauth2.googleapis.com/token"
        private const val USER_INFO_URI = "https://openidconnect.googleapis.com/v1/userinfo"

        // spring-security-oauth2-core의 상수(OidcScopes/AuthorizationGrantType) 대신 원시 문자열을
        // 쓴다. 이 프로젝트는 Spring OAuth2 Client 자동 구성을 사용하지 않아 해당 Dependency를 두지
        // 않기 위함이다(코드 리뷰 Minor 반영).
        private const val GRANT_TYPE_AUTHORIZATION_CODE = "authorization_code"
        private const val ACCESS_TOKEN_FIELD = "access_token"
        private const val SUBJECT_FIELD = "sub"
        private const val EMAIL_FIELD = "email"
        private const val EMAIL_VERIFIED_FIELD = "email_verified"
        private const val TOKEN_BYTE_LENGTH = 32
    }
}
