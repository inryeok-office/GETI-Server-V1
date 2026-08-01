package team.inreok.getiserver.domain.auth.service.impl

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import team.inreok.getiserver.domain.auth.exception.OAuthLoginFailedException
import team.inreok.getiserver.domain.auth.exception.OAuthStateInvalidException
import team.inreok.getiserver.domain.auth.exception.UnsupportedOAuthProviderException
import team.inreok.getiserver.domain.auth.service.OAuthAuthorization
import team.inreok.getiserver.domain.auth.service.OAuthUserInfo
import java.security.SecureRandom
import java.util.Base64

/**
 * DataGSM(DG) OAuth2(학생·졸업생 로그인) Provider Client다. Google과 흐름은 같지만 두 가지가 다르다.
 * 1) Token 교환 Body가 form이 아니라 JSON이라 Spring 기본 OAuth2 Client로 처리할 수 없어 [RestClient]로
 *    직접 교환한다(client_secret 방식). 2) PKCE를 사용하지 않고 `state`로만 CSRF를 방지한다.
 * DG의 `oauth_subject`는 `/userinfo`의 최상위 `id`를 사용한다(`student.id`가 아님,
 * docs/architecture/erd.md "OAuth / 회원 정책 요약" 참고). Google과 동일하게 Client 정보가 비어 있어도
 * (DG 미설정) App은 정상 기동하고, DG 로그인만 실패한다.
 */
@Component
class DgOAuthProviderClient(
    @Value("\${app.oauth.dg.client-id:}") private val clientId: String,
    @Value("\${app.oauth.dg.client-secret:}") private val clientSecret: String,
    @Value("\${app.oauth.dg.redirect-uri:}") private val redirectUri: String,
    @Value("\${app.oauth.dg.authorization-server:https://oauth.authorization.datagsm.kr}")
    private val authorizationServer: String,
    @Value("\${app.oauth.dg.resource-server:https://oauth.resource.datagsm.kr}")
    private val resourceServer: String,
    private val oAuthStateStore: OAuthStateStore,
    restClientBuilder: RestClient.Builder,
) : OAuthProviderClient {
    private val restClient = restClientBuilder.build()

    override val provider: String = "dg"

    override fun createAuthorization(): OAuthAuthorization {
        requireConfigured()
        val state = randomUrlSafeToken()
        // DG는 PKCE를 쓰지 않으므로 code_verifier 없이 CSRF 검증용 Marker만 저장한다.
        oAuthStateStore.save(state, STATE_MARKER)

        val authorizationUrl =
            UriComponentsBuilder
                .fromUriString("$authorizationServer$AUTHORIZE_PATH")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("state", state)
                // scope 파라미터는 생략한다(DG에 등록된 권한 전체가 부여됨).
                .build(true)
                .toUriString()

        return OAuthAuthorization(authorizationUrl = authorizationUrl, state = state)
    }

    override fun exchangeCode(
        code: String,
        state: String,
    ): OAuthUserInfo {
        requireConfigured()
        // 저장된 state가 없으면(만료·위조) 거부한다.
        oAuthStateStore.consume(state) ?: throw OAuthStateInvalidException()
        val accessToken = requestAccessToken(code)
        return fetchUserInfo(accessToken)
    }

    private fun requireConfigured() {
        if (clientId.isBlank() || clientSecret.isBlank() || redirectUri.isBlank()) {
            throw UnsupportedOAuthProviderException(provider)
        }
    }

    private fun requestAccessToken(code: String): String {
        // DG Token Endpoint는 application/json Body를 받는다(spec: grant_type/code/client_id/redirect_uri/client_secret).
        val body =
            mapOf(
                "grant_type" to GRANT_TYPE_AUTHORIZATION_CODE,
                "code" to code,
                "client_id" to clientId,
                "redirect_uri" to redirectUri,
                "client_secret" to clientSecret,
            )
        val response =
            runCatching {
                restClient
                    .post()
                    .uri("$authorizationServer$TOKEN_PATH")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(object : ParameterizedTypeReference<Map<String, Any?>>() {})
            }.getOrNull()

        return response?.get(ACCESS_TOKEN_FIELD) as? String ?: throw OAuthLoginFailedException()
    }

    private fun fetchUserInfo(accessToken: String): OAuthUserInfo {
        val response =
            runCatching {
                restClient
                    .get()
                    .uri("$resourceServer$USER_INFO_PATH")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                    .retrieve()
                    .body(object : ParameterizedTypeReference<Map<String, Any?>>() {})
            }.getOrNull()

        // oauth_subject는 /userinfo 최상위 id다(숫자로 올 수 있어 문자열로 변환). email Field 이름이
        // DG 실제 응답과 다르면 이 부분만 조정한다.
        val subject = response?.get(ID_FIELD)?.toString()
        val email = response?.get(EMAIL_FIELD) as? String
        if (subject.isNullOrBlank() || email == null) throw OAuthLoginFailedException()
        return OAuthUserInfo(subject = subject, email = email)
    }

    private fun randomUrlSafeToken(): String {
        val bytes = ByteArray(TOKEN_BYTE_LENGTH)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        // spring-security-oauth2-core의 AuthorizationGrantType 대신 원시 문자열(Spring OAuth2 Client
        // 자동 구성을 쓰지 않아 해당 Dependency 미사용, 코드 리뷰 Minor 반영).
        private const val GRANT_TYPE_AUTHORIZATION_CODE = "authorization_code"
        private const val AUTHORIZE_PATH = "/v1/oauth/authorize"
        private const val TOKEN_PATH = "/v1/oauth/token"
        private const val USER_INFO_PATH = "/userinfo"
        private const val STATE_MARKER = "1"
        private const val ACCESS_TOKEN_FIELD = "access_token"
        private const val ID_FIELD = "id"
        private const val EMAIL_FIELD = "email"
        private const val TOKEN_BYTE_LENGTH = 32
    }
}
