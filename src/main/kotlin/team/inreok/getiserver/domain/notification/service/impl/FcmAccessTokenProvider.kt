package team.inreok.getiserver.domain.notification.service.impl

import io.jsonwebtoken.Jwts
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.body
import team.inreok.getiserver.domain.notification.config.FcmHttpConfig
import team.inreok.getiserver.domain.notification.config.FcmProperties
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.time.LocalDateTime
import java.util.Date
import java.util.concurrent.atomic.AtomicReference

/**
 * Google Service Account 인증(JWT-bearer, RFC 7523)으로 FCM HTTP v1 API에 쓸 OAuth2 Access
 * Token을 발급·caching한다(Issue #190).
 *
 * Firebase Admin SDK나 google-auth-library 같은 새 Dependency를 추가하지 않는다 -- 저장소가 이미
 * 가진 jjwt(JWT 서명)와 RestClient(HTTP)만으로 표준 OAuth2 Service Account Flow
 * (`urn:ietf:params:oauth:grant-type:jwt-bearer`)를 직접 구현할 수 있다.
 *
 * Token은 Instance 안에서만 Memory Caching한다(별도 분산 Cache 없음) -- 만료 전에는 매 전송마다
 * Google에 새로 요청하지 않기 위해서다. 여러 Instance가 각자 캐싱해도 Google 쪽에서 문제되지
 * 않는다(Service Account 표준 사용 방식).
 */
@Component
class FcmAccessTokenProvider(
    @param:Qualifier(FcmHttpConfig.FCM_REST_CLIENT) private val restClient: RestClient,
    private val properties: FcmProperties,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(FcmAccessTokenProvider::class.java)
    private val cached = AtomicReference<CachedToken?>()

    /** 설정이 없거나 파싱·발급에 실패하면 null이다. 호출부가 이를 재시도 가능 실패로 해석한다. */
    fun currentAccessToken(): String? {
        val now = LocalDateTime.now()
        cached.get()?.let { token ->
            if (token.expiresAt.isAfter(now.plusSeconds(EXPIRY_MARGIN_SECONDS))) return token.value
        }
        return refresh(now)
    }

    fun projectId(): String? = serviceAccount()?.projectId

    @Suppress("ReturnCount")
    private fun refresh(now: LocalDateTime): String? {
        val account = serviceAccount() ?: return null
        val assertion = buildAssertion(account)
        return try {
            val form =
                LinkedMultiValueMap<String, String>().apply {
                    add("grant_type", JWT_BEARER_GRANT_TYPE)
                    add("assertion", assertion)
                }
            val response =
                restClient
                    .post()
                    .uri(account.tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body<Map<*, *>>()
            val accessToken = response?.get("access_token") as? String
            val expiresIn = (response?.get("expires_in") as? Number)?.toLong() ?: DEFAULT_EXPIRES_IN_SECONDS
            if (accessToken.isNullOrBlank()) {
                log.warn("Google OAuth2 응답에 access_token이 없습니다.")
                return null
            }
            cached.set(CachedToken(accessToken, now.plusSeconds(expiresIn)))
            accessToken
        } catch (ex: RestClientException) {
            log.warn("Google OAuth2 Access Token 발급 실패", ex)
            null
        }
    }

    private fun buildAssertion(account: FcmServiceAccount): String {
        val issuedAt = Instant.now()
        return Jwts
            .builder()
            .claim("iss", account.clientEmail)
            .claim("scope", FCM_SCOPE)
            .claim("aud", account.tokenUri)
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(issuedAt.plusSeconds(JWT_TTL_SECONDS)))
            .signWith(account.privateKey)
            .compact()
    }

    // Service Account JSON 자체는 요청마다 다시 파싱한다 -- 설정은 기동 이후에도 바뀌지 않는
    // 값이라 비용이 무시할 수준이고, 매번 새 PrivateKey 객체를 만들어도 성능에 영향이 없다
    // (실제 병목은 Google 호출이지 로컬 파싱이 아니다). Parsing 실패는 null로 처리해 Provider
    // 미설정과 같은 경로(재시도 가능)로 다룬다.
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    private fun serviceAccount(): FcmServiceAccount? {
        if (!properties.isConfigured()) return null
        return try {
            val node = objectMapper.readTree(properties.serviceAccountKey)
            val projectId = node.get("project_id")?.takeIf { it.isTextual }?.asText()
            val clientEmail = node.get("client_email")?.takeIf { it.isTextual }?.asText()
            val privateKeyPem = node.get("private_key")?.takeIf { it.isTextual }?.asText()
            val tokenUri =
                node
                    .get("token_uri")
                    ?.takeIf { it.isTextual }
                    ?.asText()
                    ?.takeIf { it.isNotBlank() } ?: DEFAULT_TOKEN_URI
            if (projectId.isNullOrBlank() || clientEmail.isNullOrBlank() || privateKeyPem.isNullOrBlank()) {
                log.warn("FCM Service Account JSON에 필수 필드(project_id/client_email/private_key)가 없습니다.")
                return null
            }
            FcmServiceAccount(
                projectId = projectId,
                clientEmail = clientEmail,
                privateKey = parseRsaPrivateKey(privateKeyPem),
                tokenUri = tokenUri,
            )
        } catch (ex: RuntimeException) {
            // JSON 파싱 실패, Base64 디코딩 실패, RSA Key 형식 오류 등을 모두 여기서 넓게 잡는다
            // -- 애플리케이션 기동을 막지 않고(Fail-Fast 아님) 재시도 가능 실패로 흡수한다.
            log.warn("FCM Service Account 설정을 해석하지 못했습니다.", ex)
            null
        }
    }

    private data class CachedToken(
        val value: String,
        val expiresAt: LocalDateTime,
    )

    private companion object {
        const val FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
        const val DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token"
        const val JWT_BEARER_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer"
        const val JWT_TTL_SECONDS = 3_600L
        const val DEFAULT_EXPIRES_IN_SECONDS = 3_600L

        // Google 응답 만료 시각보다 이 정도 여유를 두고 미리 갱신해, 만료 직전 요청이 실패하지
        // 않게 한다.
        const val EXPIRY_MARGIN_SECONDS = 60L
    }
}
