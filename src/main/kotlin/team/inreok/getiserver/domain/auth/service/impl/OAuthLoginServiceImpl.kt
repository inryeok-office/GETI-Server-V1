package team.inreok.getiserver.domain.auth.service.impl

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.oidc.OidcScopes
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import team.inreok.getiserver.domain.auth.exception.OAuthLoginFailedException
import team.inreok.getiserver.domain.auth.exception.OAuthStateInvalidException
import team.inreok.getiserver.domain.auth.exception.UnsupportedOAuthProviderException
import team.inreok.getiserver.domain.auth.service.OAuthAuthorization
import team.inreok.getiserver.domain.auth.service.OAuthLoginService
import team.inreok.getiserver.domain.auth.service.OAuthUserInfo
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64

/**
 * Google OAuth2(교직원 로그인)만 지원한다. `spring.security.oauth2.client.registration.*`를
 * 쓰지 않고 [googleClientId] 등 원시 값을 직접 읽어 인가 URL/Token 교환을 수행하는 이유는
 * application.yaml의 주석(app.oauth.google.*) 참고 — Client 정보가 비어 있어도(Google 미설정)
 * App이 정상 기동해야 하기 때문이다. Authorization Code Flow + PKCE(S256)를 사용하며,
 * state -> code_verifier 매핑은 Redis에 짧은 TTL로만 보관한다(PostgreSQL에 저장하지 않음,
 * docs/architecture/erd.md "OAuth / 회원 정책 요약" 참고).
 */
@Service
class OAuthLoginServiceImpl(
    @Value("\${app.oauth.google.client-id:}") private val googleClientId: String,
    @Value("\${app.oauth.google.client-secret:}") private val googleClientSecret: String,
    @Value("\${app.oauth.google.redirect-uri:}") private val googleRedirectUri: String,
    @Value("\${app.oauth.google.scope:${OidcScopes.OPENID},email,profile}") private val googleScope: String,
    private val stringRedisTemplate: StringRedisTemplate,
    restClientBuilder: RestClient.Builder,
) : OAuthLoginService {
    private val restClient = restClientBuilder.build()

    override fun getAuthorizationUrl(provider: String): OAuthAuthorization {
        requireGoogleConfigured(provider)
        val state = randomUrlSafeToken()
        val codeVerifier = randomUrlSafeToken()
        stringRedisTemplate.opsForValue().set(stateKey(state), codeVerifier, STATE_TTL)

        val authorizationUrl =
            UriComponentsBuilder
                .fromUriString(GOOGLE_AUTHORIZATION_URI)
                .queryParam("client_id", googleClientId)
                .queryParam("redirect_uri", googleRedirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", googleScope)
                .queryParam("state", state)
                .queryParam("code_challenge", codeChallenge(codeVerifier))
                .queryParam("code_challenge_method", "S256")
                .queryParam("access_type", "offline")
                .build(true)
                .toUriString()

        return OAuthAuthorization(authorizationUrl = authorizationUrl, state = state)
    }

    override fun exchangeCode(
        provider: String,
        code: String,
        state: String,
    ): OAuthUserInfo {
        requireGoogleConfigured(provider)
        val codeVerifier = consumeCodeVerifier(state)
        val accessToken = exchangeCodeForAccessToken(code, codeVerifier)
        return fetchUserInfo(accessToken)
    }

    private fun requireGoogleConfigured(provider: String) {
        if (!provider.equals(GOOGLE_PROVIDER, ignoreCase = true)) {
            throw UnsupportedOAuthProviderException(provider)
        }
        if (googleClientId.isBlank() || googleClientSecret.isBlank() || googleRedirectUri.isBlank()) {
            throw UnsupportedOAuthProviderException(provider)
        }
    }

    private fun consumeCodeVerifier(state: String): String {
        val key = stateKey(state)
        val codeVerifier = stringRedisTemplate.opsForValue().get(key) ?: throw OAuthStateInvalidException()
        stringRedisTemplate.delete(key)
        return codeVerifier
    }

    private fun exchangeCodeForAccessToken(
        code: String,
        codeVerifier: String,
    ): String {
        val body =
            LinkedMultiValueMap<String, String>().apply {
                add("grant_type", AuthorizationGrantType.AUTHORIZATION_CODE.value)
                add("code", code)
                add("redirect_uri", googleRedirectUri)
                add("client_id", googleClientId)
                add("client_secret", googleClientSecret)
                add("code_verifier", codeVerifier)
            }
        val response =
            runCatching {
                restClient
                    .post()
                    .uri(GOOGLE_TOKEN_URI)
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
                    .uri(GOOGLE_USER_INFO_URI)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                    .retrieve()
                    .body(object : ParameterizedTypeReference<Map<String, Any?>>() {})
            }.getOrNull()

        val subject = response?.get(SUBJECT_FIELD) as? String
        val email = response?.get(EMAIL_FIELD) as? String
        if (subject == null || email == null) throw OAuthLoginFailedException("Google 로그인에 실패했습니다.")
        return OAuthUserInfo(subject = subject, email = email)
    }

    private fun stateKey(state: String): String = "$STATE_KEY_PREFIX$state"

    private fun randomUrlSafeToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun codeChallenge(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    companion object {
        private const val GOOGLE_PROVIDER = "google"
        private const val GOOGLE_AUTHORIZATION_URI = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val GOOGLE_TOKEN_URI = "https://oauth2.googleapis.com/token"
        private const val GOOGLE_USER_INFO_URI = "https://openidconnect.googleapis.com/v1/userinfo"
        private const val STATE_KEY_PREFIX = "auth:oauth:state:"
        private val STATE_TTL: Duration = Duration.ofMinutes(10)
        private const val ACCESS_TOKEN_FIELD = "access_token"
        private const val SUBJECT_FIELD = "sub"
        private const val EMAIL_FIELD = "email"
    }
}
